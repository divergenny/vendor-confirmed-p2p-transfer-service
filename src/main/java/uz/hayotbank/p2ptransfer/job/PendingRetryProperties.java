package uz.hayotbank.p2ptransfer.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("transfers.pending-retry")
public record PendingRetryProperties(

        @NotNull
        Duration olderThan,

        @Min(1)
        int batchSize
) {
}
