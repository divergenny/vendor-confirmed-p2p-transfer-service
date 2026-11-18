package uz.hayotbank.p2ptransfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransferRequest(

        @Schema(example = "1")
        @NotNull
        @Positive
        Long senderId,

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
