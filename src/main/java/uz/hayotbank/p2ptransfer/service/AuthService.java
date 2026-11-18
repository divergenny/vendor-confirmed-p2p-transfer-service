package uz.hayotbank.p2ptransfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hayotbank.p2ptransfer.dto.LoginRequest;
import uz.hayotbank.p2ptransfer.dto.TokenResponse;
import uz.hayotbank.p2ptransfer.entity.User;
import uz.hayotbank.p2ptransfer.repository.UserRepository;
import uz.hayotbank.p2ptransfer.security.TokenService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        // the same answer for an unknown user and a wrong password, so nobody can find out which logins exist
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Wrong username or password");
        }
        return tokenService.createToken(user.getId(), user.getUsername());
    }
}
