package uz.hayotbank.p2ptransfer.vendor;

import java.util.UUID;

/**
 * We did not get a usable answer, so we don't know what the vendor decided.
 * The transfer must not be finished or canceled on this: ask again later with the same reference.
 */
public class VendorUnavailableException extends RuntimeException {

    public VendorUnavailableException(UUID reference, Throwable cause) {
        super("No answer from the vendor for transfer " + reference, cause);
    }

    public VendorUnavailableException(UUID reference, String reason) {
        super("Unexpected answer from the vendor for transfer " + reference + ": " + reason);
    }
}
