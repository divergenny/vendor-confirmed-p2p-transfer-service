package uz.hayotbank.p2ptransfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// there is no senderId: the money always goes from the user of the token
public record CreateTransferRequest(

        @Schema(example = "2")
        @NotNull
        @Positive
        Long recipientId,

        @Schema(example = "150000.00")
        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount
) {
}
