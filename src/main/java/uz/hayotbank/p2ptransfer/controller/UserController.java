package uz.hayotbank.p2ptransfer.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.hayotbank.p2ptransfer.dto.CreateUserRequest;
import uz.hayotbank.p2ptransfer.dto.HistoryItemResponse;
import uz.hayotbank.p2ptransfer.dto.PageResponse;
import uz.hayotbank.p2ptransfer.dto.UserResponse;
import uz.hayotbank.p2ptransfer.service.HistoryService;
import uz.hayotbank.p2ptransfer.service.UserService;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final HistoryService historyService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @GetMapping("/{id}/history")
    public PageResponse<HistoryItemResponse> getHistory(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return historyService.getHistory(id, from, to, page, size);
    }
}
