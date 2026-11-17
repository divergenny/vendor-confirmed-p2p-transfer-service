package uz.hayotbank.p2ptransfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[a-z0-9._-]*", message = "may contain only lowercase latin letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank
        @Size(max = 100)
        String fullName
) {
}
