package uz.hayotbank.p2ptransfer.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.hayotbank.p2ptransfer.IntegrationTest;
import uz.hayotbank.p2ptransfer.dto.CreateTransferRequest;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;
import uz.hayotbank.p2ptransfer.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many transfers from one sender at the same moment: the balance must never go below zero
 * and no money may appear or disappear.
 */
class ParallelTransfersTest extends IntegrationTest {

    @Autowired
    private TransferService transferService;

    @Test
    void parallelTransfersNeverSpendMoreThanTheBalance() throws Exception {
        Long sender = createUser("500.00");
        Long recipient = createUser("0.00");
        vendorAnswers("APPROVED");

        int transfers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(transfers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferStatus>> results = new ArrayList<>();
        for (int i = 0; i < transfers; i++) {
            results.add(pool.submit(() -> {
                start.await(); // all threads go at once
                return transferService.createTransfer(
                        new CreateTransferRequest(sender, recipient, new BigDecimal("100.00"))).status();
            }));
        }
        start.countDown();

        int completed = 0;
        int notEnoughMoney = 0;
        for (Future<TransferStatus> result : results) {
            try {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(TransferStatus.COMPLETED);
                completed++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(InsufficientFundsException.class);
                notEnoughMoney++;
            }
        }
        pool.shutdown();

        // 500 is enough for exactly five transfers of 100
        assertThat(completed).isEqualTo(5);
        assertThat(notEnoughMoney).isEqualTo(5);
        assertThat(balanceOf(sender)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(recipient)).isEqualByComparingTo("500.00");
    }
}
