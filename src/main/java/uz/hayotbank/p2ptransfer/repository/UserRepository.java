package uz.hayotbank.p2ptransfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uz.hayotbank.p2ptransfer.entity.User;

import java.math.BigDecimal;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    // check and withdraw in one UPDATE, so two parallel transfers can't spend the same money
    @Modifying
    @Query("update User u set u.balance = u.balance - :amount where u.id = :userId and u.balance >= :amount")
    int withdraw(Long userId, BigDecimal amount);

    @Modifying
    @Query("update User u set u.balance = u.balance + :amount where u.id = :userId")
    int deposit(Long userId, BigDecimal amount);
}
