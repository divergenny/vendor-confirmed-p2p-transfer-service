package uz.hayotbank.p2ptransfer.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import uz.hayotbank.p2ptransfer.dto.TokenResponse;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public TokenResponse createToken(Long userId, String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // "sub" is how the API knows who is calling, see CurrentUser
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttl().toSeconds());
    }
}
