package uz.hayotbank.p2ptransfer.vendor;

import java.math.BigDecimal;
import java.util.UUID;

// field names come from the vendor contract, that's why payer/payee and not sender/recipient
public record PaymentRequest(UUID reference, String payer, String payee, BigDecimal amount) {
}
