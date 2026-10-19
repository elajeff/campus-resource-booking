package campus.booking;

import java.time.LocalDateTime;
import java.util.List;

/** Persistence operations used by the booking service. */
public interface BookingRepository {
    List<Resource> loadResources();

    List<Reservation> loadReservations();

    void addResource(Resource resource);

    Reservation createReservation(String resourceId, String borrowerName,
                                  LocalDateTime start, LocalDateTime end);

    boolean cancelReservation(String reservationId);

    boolean retireResource(String resourceId, LocalDateTime activeAfter);
}
