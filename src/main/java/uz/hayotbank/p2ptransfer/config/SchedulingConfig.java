package uz.hayotbank.p2ptransfer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// tests switch it off and run the job by hand, so it never fires in the middle of another test
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "transfers.pending-retry.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
