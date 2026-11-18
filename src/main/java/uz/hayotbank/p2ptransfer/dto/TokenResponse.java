package uz.hayotbank.p2ptransfer.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
