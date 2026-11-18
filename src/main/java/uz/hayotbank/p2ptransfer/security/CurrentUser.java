package uz.hayotbank.p2ptransfer.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    // TokenService puts the user id into "sub" on login
    public static Long id(Jwt token) {
        return Long.valueOf(token.getSubject());
    }
}
