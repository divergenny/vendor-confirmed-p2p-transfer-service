package uz.hayotbank.p2ptransfer.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// our own page format, so the JSON doesn't depend on Spring Data internals
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
