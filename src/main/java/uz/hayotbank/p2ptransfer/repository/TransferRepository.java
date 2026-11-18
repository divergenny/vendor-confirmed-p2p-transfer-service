package uz.hayotbank.p2ptransfer.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
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
}
