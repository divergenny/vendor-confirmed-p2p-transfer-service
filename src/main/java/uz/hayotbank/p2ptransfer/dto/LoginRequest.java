package uz.hayotbank.p2ptransfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(example = "aziz")
        @NotBlank
        String username,

        @Schema(example = "password")
        @NotBlank
        String password
) {
}
