package uz.hayotbank.p2ptransfer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hayotbank.p2ptransfer.dto.CreateUserRequest;
import uz.hayotbank.p2ptransfer.dto.UserResponse;
import uz.hayotbank.p2ptransfer.entity.User;
import uz.hayotbank.p2ptransfer.exception.NotFoundException;
import uz.hayotbank.p2ptransfer.exception.UsernameAlreadyTakenException;
import uz.hayotbank.p2ptransfer.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createsUserWithZeroBalance() {
        when(userRepository.existsByUsername("bekzod")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse user = userService.createUser(new CreateUserRequest("bekzod", "Bekzod Nazarov"));

        assertThat(user.username()).isEqualTo("bekzod");
        assertThat(user.fullName()).isEqualTo("Bekzod Nazarov");
        assertThat(user.balance()).isEqualByComparingTo("0");
    }

    @Test
    void doesNotSaveUserWithTakenUsername() {
        when(userRepository.existsByUsername("aziz")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest("aziz", "Aziz Karimov")))
                .isInstanceOf(UsernameAlreadyTakenException.class)
                .hasMessage("Username 'aziz' is already taken");
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 42 not found");
    }
}
