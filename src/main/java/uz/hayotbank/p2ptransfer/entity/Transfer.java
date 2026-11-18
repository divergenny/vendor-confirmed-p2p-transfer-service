package uz.hayotbank.p2ptransfer.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // sent to the vendor: the same reference on a retry means "the same operation"
    @Column(nullable = false, unique = true, updatable = false)
    private UUID reference;

    // plain ids instead of @ManyToOne: a transfer never needs the whole user object
    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private Long recipientId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transfer() {

    }

    public Transfer(Long senderId, Long recipientId, BigDecimal amount) {
        this.reference = UUID.randomUUID();
        this.senderId = senderId;
        this.recipientId = recipientId;
        // 150000 and 150000.00 are the same money, but the vendor always gets it as 150000.00
        this.amount = amount.setScale(2);
        this.status = TransferStatus.PENDING;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    public boolean isPending() {
        return status == TransferStatus.PENDING;
    }

    public void finish(TransferStatus result) {
        this.status = result;
        this.updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS); // Postgres keeps microseconds
    }

    public Long getId() {
        return id;
    }

    public UUID getReference() {
        return reference;
    }

    public Long getSenderId() {
        return senderId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
