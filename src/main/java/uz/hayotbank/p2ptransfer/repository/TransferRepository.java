package uz.hayotbank.p2ptransfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hayotbank.p2ptransfer.entity.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

}
