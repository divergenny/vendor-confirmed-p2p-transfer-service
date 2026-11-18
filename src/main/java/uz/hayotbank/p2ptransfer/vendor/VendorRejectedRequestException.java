package uz.hayotbank.p2ptransfer.vendor;

import java.util.UUID;

/**
 * The vendor answered 4xx: the request itself is wrong, so no operation was created on its side
 * and sending the same request again won't help.
 */
public class VendorRejectedRequestException extends RuntimeException {

    public VendorRejectedRequestException(UUID reference, int status, String body) {
        super("Vendor rejected the request for transfer " + reference + " with " + status + ": " + body);
    }
}
