package uz.hayotbank.p2ptransfer.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.hayotbank.p2ptransfer.entity.Transfer;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;
import uz.hayotbank.p2ptransfer.repository.TransferRepository;
import uz.hayotbank.p2ptransfer.service.TransferService;

import java.time.Instant;
import java.util.List;

/**
 * Transfers stay PENDING when the vendor didn't answer. This job asks the vendor about them again,
 * with the same reference, until they are finished one way or another.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingTransfersJob {

    // three transfers in a row without an answer: the vendor is most likely down
    private static final int MAX_FAILURES_IN_ROW = 3;

    private final TransferRepository transferRepository;
    private final TransferService transferService;
    private final PendingRetryProperties properties;

    @Scheduled(fixedDelayString = "${transfers.pending-retry.interval}")
    public void retryPendingTransfers() {
        Instant createdBefore = Instant.now().minus(properties.olderThan());
        List<Transfer> pending = transferRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAt(
                TransferStatus.PENDING, createdBefore, Limit.of(properties.batchSize()));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Asking the vendor again about {} pending transfers", pending.size());

        int failuresInRow = 0;
        for (Transfer transfer : pending) {
            if (finished(transfer)) {
                failuresInRow = 0;
            } else {
                failuresInRow++;
            }

            if (failuresInRow >= MAX_FAILURES_IN_ROW) {
                log.warn("Vendor still doesn't answer, the other pending transfers wait for the next run");
                return;
            }
        }
    }

    private boolean finished(Transfer transfer) {
        try {
            return !transferService.confirmWithVendor(transfer).isPending();
        } catch (RuntimeException e) {
            // one broken transfer must not stop the others
            log.error("Could not retry transfer {}", transfer.getId(), e);
            return false;
        }
    }
}
