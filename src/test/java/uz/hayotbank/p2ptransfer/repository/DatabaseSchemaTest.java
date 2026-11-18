package uz.hayotbank.p2ptransfer.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import uz.hayotbank.p2ptransfer.IntegrationTest;
import uz.hayotbank.p2ptransfer.entity.Transfer;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;
import uz.hayotbank.p2ptransfer.entity.User;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the Liquibase changelogs on a real Postgres and checks
 * the test data, the entity mapping and the constraints that protect the money.
 */
@Transactional // every test is rolled back, so tests don't see each other's rows
class DatabaseSchemaTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void devDataIsLoaded() {
        assertThat(userRepository.findAll())
                .extracting(User::getUsername)
                .contains("aziz", "ivan", "dilnoza", "elena");

        assertThat(transferRepository.findAll())
                .extracting(Transfer::getStatus)
                .contains(TransferStatus.COMPLETED, TransferStatus.DECLINED);
    }

    @Test
    void balanceCannotBeNegative() {
        User user = userRepository.save(new User("negative-balance", "Negative Balance", null));

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE users SET balance = -1 WHERE id = ?", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transferToYourselfIsRejected() {
        User user = userRepository.save(new User("self-transfer", "Self Transfer", null));

        assertThatThrownBy(() -> transferRepository.saveAndFlush(
                new Transfer(user.getId(), user.getId(), new BigDecimal("10.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transferAmountMustBePositive() {
        User sender = userRepository.save(new User("zero-sender", "Zero Sender", null));
        User recipient = userRepository.save(new User("zero-recipient", "Zero Recipient", null));

        assertThatThrownBy(() -> transferRepository.saveAndFlush(
                new Transfer(sender.getId(), recipient.getId(), new BigDecimal("0.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
