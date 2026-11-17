package uz.hayotbank.p2ptransfer.dto;

import uz.hayotbank.p2ptransfer.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        BigDecimal balance,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getBalance(), user.getCreatedAt());
    }
}
