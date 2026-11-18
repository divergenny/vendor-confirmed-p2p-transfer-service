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
        String fullName,

        // bcrypt only looks at the first 72 bytes, so a longer password would be cut silently
        @Schema(example = "bekzod-secret")
        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
}
