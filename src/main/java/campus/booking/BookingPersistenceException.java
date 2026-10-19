package campus.booking;

/** An unchecked wrapper for database failures that the UI can report clearly. */
public class BookingPersistenceException extends RuntimeException {
    public BookingPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
