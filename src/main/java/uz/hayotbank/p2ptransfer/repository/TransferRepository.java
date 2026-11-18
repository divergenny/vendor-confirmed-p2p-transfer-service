package uz.hayotbank.p2ptransfer.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import uz.hayotbank.p2ptransfer.entity.Transfer;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    // select ... for update: while one transaction finishes the transfer, nobody else can touch it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> findByIdForUpdate(Long id);

    List<Transfer> findByStatusAndCreatedAtBeforeOrderByCreatedAt(TransferStatus status, Instant createdBefore, Limit limit);

    // the user can be on either side of a transfer; both cases are covered by the indexes from changelog 002
    @Query("""
            select t from Transfer t
            where (t.senderId = :userId or t.recipientId = :userId)
              and t.createdAt >= :from and t.createdAt < :to
            """)
    Page<Transfer> findHistory(Long userId, Instant from, Instant to, Pageable pageable);
}
