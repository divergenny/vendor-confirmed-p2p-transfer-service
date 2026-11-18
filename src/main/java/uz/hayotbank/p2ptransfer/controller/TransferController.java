package uz.hayotbank.p2ptransfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.hayotbank.p2ptransfer.dto.CreateTransferRequest;
import uz.hayotbank.p2ptransfer.dto.TransferResponse;
import uz.hayotbank.p2ptransfer.entity.TransferStatus;
import uz.hayotbank.p2ptransfer.security.CurrentUser;
import uz.hayotbank.p2ptransfer.service.TransferService;

import java.net.URI;

@Tag(name = "Transfers")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Operation(summary = "Transfer money to another user",
            description = "The money goes from the user of the token. "
                    + "It is reserved on the sender's balance first, then the vendor is asked to confirm. "
                    + "201 means the transfer is finished, 202 means the vendor has not answered yet "
                    + "and the transfer stays PENDING until a background job finishes it.")
    @ApiResponse(responseCode = "201", description = "Finished: COMPLETED, DECLINED or FAILED")
    @ApiResponse(responseCode = "202", description = "Waiting for the vendor: PENDING")
    @ApiResponse(responseCode = "400", description = "Invalid fields or a transfer to yourself")
    @ApiResponse(responseCode = "401", description = "No token, or it is broken or expired")
    @ApiResponse(responseCode = "404", description = "Recipient not found")
    @ApiResponse(responseCode = "422", description = "Not enough money on the sender's balance")
    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request,
                                                           @AuthenticationPrincipal Jwt token) {
        TransferResponse transfer = transferService.createTransfer(CurrentUser.id(token), request);
        // 202: the vendor hasn't answered yet, the transfer will be finished later
        HttpStatus status = transfer.status() == TransferStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/transfers/" + transfer.id()))
                .body(transfer);
    }

    @Operation(summary = "Get a transfer", description = "Only the sender and the recipient can see it.")
    @ApiResponse(responseCode = "200", description = "The transfer")
    @ApiResponse(responseCode = "401", description = "No token, or it is broken or expired")
    @ApiResponse(responseCode = "403", description = "A transfer between other users")
    @ApiResponse(responseCode = "404", description = "No such transfer")
    @GetMapping("/{id}")
    public TransferResponse getTransfer(@PathVariable Long id, @AuthenticationPrincipal Jwt token) {
        return transferService.getTransfer(id, CurrentUser.id(token));
    }
}
