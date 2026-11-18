package uz.hayotbank.p2ptransfer.exception;

public class TransferToYourselfException extends RuntimeException {

    public TransferToYourselfException() {
        super("Sender and recipient must be different users");
    }
}
