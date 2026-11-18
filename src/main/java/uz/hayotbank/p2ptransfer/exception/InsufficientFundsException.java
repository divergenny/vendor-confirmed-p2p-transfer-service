package uz.hayotbank.p2ptransfer.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long userId) {
        super("User " + userId + " does not have enough money for this transfer");
    }
}
