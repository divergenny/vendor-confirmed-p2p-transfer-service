package uz.hayotbank.p2ptransfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hayotbank.p2ptransfer.dto.CreateUserRequest;
import uz.hayotbank.p2ptransfer.dto.UserResponse;
import uz.hayotbank.p2ptransfer.entity.User;
import uz.hayotbank.p2ptransfer.exception.NotFoundException;
import uz.hayotbank.p2ptransfer.exception.UsernameAlreadyTakenException;
import uz.hayotbank.p2ptransfer.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // gives a clear error in the usual case; the unique index still protects us from two requests at once
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyTakenException(request.username());
        }
        User user = userRepository.save(new User(request.username(), request.fullName()));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }
}
