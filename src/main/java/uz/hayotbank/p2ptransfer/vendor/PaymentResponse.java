package uz.hayotbank.p2ptransfer.vendor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

// the vendor may add new fields to the answer, that must not break us
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(
        UUID reference,
        PaymentStatus status
) {
}
