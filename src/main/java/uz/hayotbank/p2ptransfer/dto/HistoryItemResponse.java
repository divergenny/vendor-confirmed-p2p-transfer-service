package uz.hayotbank.p2ptransfer.dto;

import uz.hayotbank.p2ptransfer.entity.Transfer;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// one line of the history as the user sees it: money in or out, and who is on the other side
public record HistoryItemResponse(
        Long transferId,
        UUID reference,
        Direction direction,
        Long counterpartyId,
        BigDecimal amount,
        TransferStatus status,
        Instant createdAt
) {

    public enum Direction {
        INCOMING,
        OUTGOING
    }

    public static HistoryItemResponse of(Transfer transfer, Long userId) {
        boolean outgoing = transfer.getSenderId().equals(userId);
        return new HistoryItemResponse(
                transfer.getId(),
                transfer.getReference(),
                outgoing ? Direction.OUTGOING : Direction.INCOMING,
                outgoing ? transfer.getRecipientId() : transfer.getSenderId(),
                transfer.getAmount(),
                transfer.getStatus(),
                transfer.getCreatedAt()
        );
    }
}
