package uz.hayotbank.p2ptransfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hayotbank.p2ptransfer.dto.HistoryItemResponse;
import uz.hayotbank.p2ptransfer.dto.PageResponse;
import uz.hayotbank.p2ptransfer.exception.InvalidPeriodException;
import uz.hayotbank.p2ptransfer.exception.NotFoundException;
import uz.hayotbank.p2ptransfer.repository.TransferRepository;
import uz.hayotbank.p2ptransfer.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final HistoryProperties properties;

    @Transactional(readOnly = true)
    public PageResponse<HistoryItemResponse> getHistory(Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (from.isAfter(to)) {
            throw new InvalidPeriodException(from, to);
        }
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User " + userId + " not found");
        }

        // whole days in the bank's time zone: from 00:00 of "from" up to 00:00 of the day after "to"
        ZoneId zone = properties.timeZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        // newest first; id breaks ties, so a transfer can't jump between pages
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<HistoryItemResponse> history = transferRepository.findHistory(userId, start, end, pageable)
                .map(transfer -> HistoryItemResponse.of(transfer, userId));
        return PageResponse.from(history);
    }
}
