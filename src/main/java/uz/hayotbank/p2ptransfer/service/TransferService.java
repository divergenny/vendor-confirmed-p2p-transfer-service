package uz.hayotbank.p2ptransfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import uz.hayotbank.p2ptransfer.dto.CreateTransferRequest;
import uz.hayotbank.p2ptransfer.dto.TransferResponse;
import uz.hayotbank.p2ptransfer.entity.Transfer;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;
import uz.hayotbank.p2ptransfer.exception.InsufficientFundsException;
import uz.hayotbank.p2ptransfer.exception.NotFoundException;
import uz.hayotbank.p2ptransfer.exception.TransferToYourselfException;
import uz.hayotbank.p2ptransfer.repository.TransferRepository;
import uz.hayotbank.p2ptransfer.repository.UserRepository;
import uz.hayotbank.p2ptransfer.vendor.PaymentStatus;
import uz.hayotbank.p2ptransfer.vendor.VendorClient;
import uz.hayotbank.p2ptransfer.vendor.VendorRejectedRequestException;
import uz.hayotbank.p2ptransfer.vendor.VendorUnavailableException;

/**
 * A transfer goes in three steps:
 * 1. short transaction: take the money from the sender (reserve it) and save the transfer as PENDING;
 * 2. ask the vendor outside of any transaction, the call may take tens of seconds;
 * 3. short transaction: give the money to the recipient, or back to the sender.
 * If the vendor doesn't answer, the transfer stays PENDING and the money stays reserved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final VendorClient vendorClient;
    private final TransactionTemplate transactionTemplate;

    public TransferResponse createTransfer(CreateTransferRequest request) {
        Transfer transfer = transactionTemplate.execute(status -> reserveMoney(request));
        return TransferResponse.from(confirmWithVendor(transfer));
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long id) {
        return transferRepository.findById(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new NotFoundException("Transfer " + id + " not found"));
    }

    private Transfer reserveMoney(CreateTransferRequest request) {
        Long senderId = request.senderId();
        Long recipientId = request.recipientId();
        if (senderId.equals(recipientId)) {
            throw new TransferToYourselfException();
        }
        checkUserExists(senderId);
        checkUserExists(recipientId);

        Transfer transfer = new Transfer(senderId, recipientId, request.amount());
        if (userRepository.withdraw(senderId, transfer.getAmount()) == 0) {
            throw new InsufficientFundsException(senderId);
        }
        return transferRepository.save(transfer);
    }

    private Transfer confirmWithVendor(Transfer transfer) {
        PaymentStatus answer;
        try {
            answer = vendorClient.confirmPayment(transfer.getReference(), transfer.getSenderId(),
                    transfer.getRecipientId(), transfer.getAmount());
        } catch (VendorRejectedRequestException e) {
            log.error("Vendor rejected transfer {}: {}", transfer.getId(), e.getMessage());
            return transactionTemplate.execute(status -> finish(transfer.getId(), TransferStatus.FAILED));
        } catch (VendorUnavailableException e) {
            // we don't know what the vendor decided: keep the money reserved and ask again later
            log.warn("Transfer {} stays PENDING: {}", transfer.getId(), e.getMessage());
            return transfer;
        }

        TransferStatus result = answer == PaymentStatus.APPROVED ? TransferStatus.COMPLETED : TransferStatus.DECLINED;
        return transactionTemplate.execute(status -> finish(transfer.getId(), result));
    }

    private Transfer finish(Long transferId, TransferStatus result) {
        // the row lock makes sure nobody else finishes the same transfer at the same time
        Transfer transfer = transferRepository.findByIdForUpdate(transferId).orElseThrow();
        if (!transfer.isPending()) {
            return transfer;
        }

        Long moneyGoesTo = result == TransferStatus.COMPLETED ? transfer.getRecipientId() : transfer.getSenderId();
        userRepository.deposit(moneyGoesTo, transfer.getAmount());
        transfer.finish(result);
        log.info("Transfer {} is {}", transferId, result);
        return transfer;
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User " + userId + " not found");
        }
    }
}
