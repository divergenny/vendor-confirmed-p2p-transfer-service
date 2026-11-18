package uz.hayotbank.p2ptransfer.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

@Validated
@ConfigurationProperties("transfers.history")
public record HistoryProperties(

        @NotNull
        ZoneId timeZone
) {
}
