package uz.hayotbank.p2ptransfer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import uz.hayotbank.p2ptransfer.exception.InvalidPeriodException;
import uz.hayotbank.p2ptransfer.exception.NotFoundException;
import uz.hayotbank.p2ptransfer.repository.TransferRepository;
import uz.hayotbank.p2ptransfer.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private UserRepository userRepository;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new HistoryService(transferRepository, userRepository,
                new HistoryProperties(ZoneId.of("Asia/Tashkent")));
    }

    @Test
    void daysAreCountedInBankTimeZone() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(transferRepository.findHistory(any(), any(), any(), any())).thenReturn(Page.empty());

        historyService.getHistory(1L, LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14), 0, 20);

        // Tashkent is UTC+5: its 14th of November lasts from 13th 19:00 to 14th 19:00 in UTC
        verify(transferRepository).findHistory(eq(1L),
                eq(Instant.parse("2026-11-13T19:00:00Z")),
                eq(Instant.parse("2026-11-14T19:00:00Z")),
                any());
    }

    @Test
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> historyService.getHistory(1L,
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), 0, 20))
                .isInstanceOf(InvalidPeriodException.class);
        verifyNoInteractions(transferRepository);
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> historyService.getHistory(42L,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 0, 20))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 42 not found");
    }
}
