package uz.hayotbank.p2ptransfer.exception;

import java.time.LocalDate;

public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException(LocalDate from, LocalDate to) {
        super("'from' (" + from + ") must not be after 'to' (" + to + ")");
    }
}
