package uz.hayotbank.p2ptransfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @Schema(example = "bekzod")
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[a-z0-9._-]*", message = "may contain only lowercase latin letters, digits, '.', '_' and '-'")
        String username,

        @Schema(example = "Bekzod Nazarov")
        @NotBlank
        @Size(max = 100)
        String fullName
) {
}
