package uz.hayotbank.p2ptransfer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Long SENDER = 1L;
    private static final Long RECIPIENT = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VendorClient vendorClient;

    private TransferService transferService;
    private Transfer savedTransfer;

    @BeforeEach
    void setUp() {
        // runs the callbacks as they are, without a real database transaction
        TransactionTemplate transactions = new TransactionTemplate(mock(PlatformTransactionManager.class));
        transferService = new TransferService(transferRepository, userRepository, vendorClient, transactions);
    }

    @Test
    void approvedTransferGoesToRecipient() {
        moneyIsReserved();
        vendorAnswers(PaymentStatus.APPROVED);

        TransferResponse transfer = transferService.createTransfer(SENDER, request(RECIPIENT));

        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
        verify(vendorClient).confirmPayment(savedTransfer.getReference(), SENDER, RECIPIENT, AMOUNT);
        verify(userRepository).deposit(RECIPIENT, AMOUNT);
        verify(userRepository, never()).deposit(SENDER, AMOUNT);
    }

    @Test
    void declinedTransferReturnsMoneyToSender() {
        moneyIsReserved();
        vendorAnswers(PaymentStatus.DECLINED);

        TransferResponse transfer = transferService.createTransfer(SENDER, request(RECIPIENT));

        assertThat(transfer.status()).isEqualTo(TransferStatus.DECLINED);
        verify(userRepository).deposit(SENDER, AMOUNT);
        verify(userRepository, never()).deposit(RECIPIENT, AMOUNT);
    }

    @Test
    void rejectedRequestFailsTransferAndReturnsMoney() {
        moneyIsReserved();
        transferCanBeLocked();
        when(vendorClient.confirmPayment(any(), any(), any(), any()))
                .thenThrow(new VendorRejectedRequestException(null, 400, "bad amount"));

        TransferResponse transfer = transferService.createTransfer(SENDER, request(RECIPIENT));

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
        verify(userRepository).deposit(SENDER, AMOUNT);
    }

    @Test
    void noAnswerKeepsTransferPendingAndMoneyReserved() {
        moneyIsReserved();
        when(vendorClient.confirmPayment(any(), any(), any(), any()))
                .thenThrow(new VendorUnavailableException(null, "timeout"));

        TransferResponse transfer = transferService.createTransfer(SENDER, request(RECIPIENT));

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        verify(userRepository).withdraw(SENDER, AMOUNT);
        verify(userRepository, never()).deposit(any(), any());
    }

    @Test
    void notEnoughMoneyStopsBeforeVendor() {
        when(userRepository.existsById(any())).thenReturn(true);
        when(userRepository.withdraw(SENDER, AMOUNT)).thenReturn(0);

        assertThatThrownBy(() -> transferService.createTransfer(SENDER, request(RECIPIENT)))
                .isInstanceOf(InsufficientFundsException.class);
        verifyNoInteractions(vendorClient);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void cannotSendMoneyToYourself() {
        assertThatThrownBy(() -> transferService.createTransfer(SENDER, request(SENDER)))
                .isInstanceOf(TransferToYourselfException.class);
        verifyNoInteractions(userRepository, transferRepository, vendorClient);
    }

    @Test
    void unknownRecipientIsNotFound() {
        when(userRepository.existsById(SENDER)).thenReturn(true);
        when(userRepository.existsById(RECIPIENT)).thenReturn(false);

        assertThatThrownBy(() -> transferService.createTransfer(SENDER, request(RECIPIENT)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 2 not found");
        verify(userRepository, never()).withdraw(any(), any());
    }

    @Test
    void alreadyFinishedTransferIsNotFinishedAgain() {
        moneyIsReserved();
        when(vendorClient.confirmPayment(any(), any(), any(), any())).thenReturn(PaymentStatus.APPROVED);
        // somebody else has already completed this transfer while we were waiting for the vendor
        Transfer finishedByOthers = new Transfer(SENDER, RECIPIENT, AMOUNT);
        finishedByOthers.finish(TransferStatus.COMPLETED);
        when(transferRepository.findByIdForUpdate(any())).thenReturn(Optional.of(finishedByOthers));

        transferService.createTransfer(SENDER, request(RECIPIENT));

        verify(userRepository, never()).deposit(any(), any());
    }

    @Test
    void onlySenderAndRecipientSeeTransfer() {
        when(transferRepository.findById(10L)).thenReturn(Optional.of(new Transfer(SENDER, RECIPIENT, AMOUNT)));

        assertThat(transferService.getTransfer(10L, SENDER).amount()).isEqualByComparingTo(AMOUNT);
        assertThat(transferService.getTransfer(10L, RECIPIENT).amount()).isEqualByComparingTo(AMOUNT);
        assertThatThrownBy(() -> transferService.getTransfer(10L, 3L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void moneyIsReserved() {
        when(userRepository.existsById(any())).thenReturn(true);
        when(userRepository.withdraw(SENDER, AMOUNT)).thenReturn(1);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            savedTransfer = invocation.getArgument(0);
            return savedTransfer;
        });
    }

    private void transferCanBeLocked() {
        when(transferRepository.findByIdForUpdate(any())).thenAnswer(invocation -> Optional.of(savedTransfer));
    }

    private void vendorAnswers(PaymentStatus answer) {
        transferCanBeLocked();
        when(vendorClient.confirmPayment(any(), any(), any(), any())).thenReturn(answer);
    }

    private CreateTransferRequest request(Long recipientId) {
        return new CreateTransferRequest(recipientId, AMOUNT);
    }
}
