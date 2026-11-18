package uz.hayotbank.p2ptransfer.vendor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("vendor")
public record VendorProperties(
        @NotBlank
        String url,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout,

        @Min(1)
        int maxAttempts,

        @NotNull
        Duration retryDelay
) {
}
