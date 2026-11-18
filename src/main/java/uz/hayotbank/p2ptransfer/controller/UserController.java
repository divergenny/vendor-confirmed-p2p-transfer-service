package uz.hayotbank.p2ptransfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final HistoryService historyService;

    @Operation(summary = "Create a user", description = "A new user starts with a zero balance: top-ups are out of scope.")
    @ApiResponse(responseCode = "201", description = "The user is created")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "409", description = "The username is already taken")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.id())).body(user);
    }

    @Operation(summary = "Get a user and the balance")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No such user")
    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @Operation(summary = "Transfer history of a user",
            description = "Transfers of any status where the user is the sender or the recipient, newest first. "
                    + "Both dates are inclusive, a day is counted in the bank's time zone (Asia/Tashkent).")
    @ApiResponse(responseCode = "200", description = "One page of the history")
    @ApiResponse(responseCode = "400", description = "Invalid dates or page parameters")
    @ApiResponse(responseCode = "404", description = "No such user")
    @GetMapping("/{id}/history")
    public PageResponse<HistoryItemResponse> getHistory(
            @PathVariable Long id,
            @Parameter(description = "First day, inclusive", example = "2026-11-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive", example = "2026-11-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return historyService.getHistory(id, from, to, page, size);
    }
}
