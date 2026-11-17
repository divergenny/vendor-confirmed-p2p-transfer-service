package uz.hayotbank.p2ptransfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.hayotbank.p2ptransfer.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);
}
