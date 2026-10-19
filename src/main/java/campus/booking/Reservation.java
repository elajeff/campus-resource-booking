package campus.booking;

import java.time.LocalDateTime;

public class Reservation {
    private final String id;
    private final String resourceId;
    private final String borrowerName;
    private final LocalDateTime start;
    private final LocalDateTime end;

    public Reservation(String id, String resourceId, String borrowerName,
                       LocalDateTime start, LocalDateTime end) {
        if (isBlank(id) || isBlank(resourceId) || isBlank(borrowerName)) {
            throw new IllegalArgumentException("Reservation ID, resource ID, and name are required.");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        this.id = id;
        this.resourceId = resourceId;
        this.borrowerName = borrowerName.trim();
        this.start = start;
        this.end = end;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String getId() {
        return id;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        // Back-to-back reservations are allowed; only actual overlap is rejected.
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }
}
