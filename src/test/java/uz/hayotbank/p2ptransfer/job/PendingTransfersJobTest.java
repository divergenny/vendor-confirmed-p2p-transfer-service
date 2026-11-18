package uz.hayotbank.p2ptransfer.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hayotbank.p2ptransfer.IntegrationTest;
import uz.hayotbank.p2ptransfer.dto.CreateTransferRequest;
import uz.hayotbank.p2ptransfer.dto.TransferResponse;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;
import uz.hayotbank.p2ptransfer.service.TransferService;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class PendingTransfersJobTest extends IntegrationTest {

    @Autowired
    private PendingTransfersJob job;

    @Autowired
    private TransferService transferService;

    @BeforeEach
    void forgetOtherPendingTransfers() {
        // other tests may have left PENDING transfers behind, the job would pick them up too
        jdbcTemplate.update("UPDATE transfers SET status = 'FAILED' WHERE status = 'PENDING'");
    }

    @Test
    void finishesTransferWhenVendorIsBack() {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        TransferResponse transfer = pendingTransfer(sender, recipient, 10);
        vendorAnswers("APPROVED");

        job.retryPendingTransfers();

        assertThat(statusOf(transfer)).isEqualTo("COMPLETED");
        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("300.00");
        // 3 attempts by the request itself and 1 by the job, always with the same reference
        assertThat(requestsFor(transfer)).isEqualTo(4);
    }

    @Test
    void declinedOnRetryGivesMoneyBack() {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        TransferResponse transfer = pendingTransfer(sender, recipient, 10);
        vendorAnswers("DECLINED");

        job.retryPendingTransfers();

        assertThat(statusOf(transfer)).isEqualTo("DECLINED");
        assertThat(balanceOf(sender)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("0.00");
    }

    @Test
    void leavesFreshTransfersAlone() {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        TransferResponse transfer = pendingTransfer(sender, recipient, 0);
        vendorAnswers("APPROVED");

        job.retryPendingTransfers();

        assertThat(statusOf(transfer)).isEqualTo("PENDING");
        assertThat(requestsFor(transfer)).isEqualTo(3);
    }

    @Test
    void keepsMoneyReservedWhileVendorIsDown() {
        Long sender = createUser("1000.00");
        Long recipient = createUser("0.00");
        TransferResponse transfer = pendingTransfer(sender, recipient, 10);

        job.retryPendingTransfers();

        assertThat(statusOf(transfer)).isEqualTo("PENDING");
        assertThat(balanceOf(sender)).isEqualByComparingTo("700.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("0.00");
        assertThat(requestsFor(transfer)).isEqualTo(6);
    }

    @Test
    void stopsAfterThreeFailuresInARow() {
        Long sender = createUser("2000.00");
        Long recipient = createUser("0.00");
        TransferResponse first = pendingTransfer(sender, recipient, 40);
        TransferResponse second = pendingTransfer(sender, recipient, 30);
        TransferResponse third = pendingTransfer(sender, recipient, 20);
        TransferResponse fourth = pendingTransfer(sender, recipient, 10);

        job.retryPendingTransfers();

        // the oldest three were asked again (3 + 3 attempts), the fourth waits for the next run
        assertThat(requestsFor(first)).isEqualTo(6);
        assertThat(requestsFor(second)).isEqualTo(6);
        assertThat(requestsFor(third)).isEqualTo(6);
        assertThat(requestsFor(fourth)).isEqualTo(3);
    }

    // creates a transfer while the vendor is down and makes it look older than it is
    private TransferResponse pendingTransfer(Long sender, Long recipient, int minutesAgo) {
        vendorFailsWith(503);
        TransferResponse transfer = transferService.createTransfer(
                new CreateTransferRequest(sender, recipient, new BigDecimal("300.00")));
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        jdbcTemplate.update("UPDATE transfers SET created_at = now() - ? * interval '1 minute' WHERE id = ?",
                minutesAgo, transfer.id());
        return transfer;
    }

    private String statusOf(TransferResponse transfer) {
        return jdbcTemplate.queryForObject("SELECT status FROM transfers WHERE id = ?", String.class, transfer.id());
    }

    private int requestsFor(TransferResponse transfer) {
        return vendor.findAll(postRequestedFor(urlEqualTo(VENDOR_PAYMENTS))
                .withRequestBody(matchingJsonPath("$.reference", equalTo(transfer.reference().toString())))).size();
    }
}
