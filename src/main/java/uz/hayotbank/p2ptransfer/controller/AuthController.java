package uz.hayotbank.p2ptransfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hayotbank.p2ptransfer.dto.LoginRequest;
import uz.hayotbank.p2ptransfer.dto.TokenResponse;
import uz.hayotbank.p2ptransfer.service.AuthService;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Log in and get a token",
            description = "Send the token with every other request: `Authorization: Bearer <token>`")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "The token")
    @ApiResponse(responseCode = "400", description = "Empty username or password")
    @ApiResponse(responseCode = "401", description = "Wrong username or password")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
