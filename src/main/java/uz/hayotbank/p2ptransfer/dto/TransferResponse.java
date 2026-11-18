package uz.hayotbank.p2ptransfer.dto;

import uz.hayotbank.p2ptransfer.entity.Transfer;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        Long id,
        UUID reference,
        Long senderId,
        Long recipientId,
        BigDecimal amount,
        TransferStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getSenderId(),
                transfer.getRecipientId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }
}
