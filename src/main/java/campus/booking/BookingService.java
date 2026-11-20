package campus.booking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BookingService {
    private final Object stateLock = new Object();
    private final List<Resource> resources = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();
    private final BookingRepository repository;

    public BookingService(BookingRepository repository) {
        this.repository = repository;
        refresh();
    }

    /** Reloads the display snapshot. Call from a background task, not the Swing event thread. */
    public void refresh() {
        List<Resource> loadedResources = repository.loadResources();
        List<Reservation> loadedReservations = repository.loadReservations();
        synchronized (stateLock) {
            resources.clear();
            resources.addAll(loadedResources);
            reservations.clear();
            reservations.addAll(loadedReservations);
        }
    }

    public List<Resource> getResources() {
        synchronized (stateLock) {
            return resources.stream().filter(Resource::isActive).toList();
        }
    }

    public List<Reservation> getReservations() {
        synchronized (stateLock) {
            return List.copyOf(reservations);
        }
    }

    public Optional<Resource> findResource(String resourceId) {
        if (resourceId == null) {
            return Optional.empty();
        }
        synchronized (stateLock) {
            return findResourceLocked(resourceId);
        }
    }

    public List<Resource> availableResources(LocalDateTime start, LocalDateTime end) {
        validateTimeRange(start, end);
        synchronized (stateLock) {
            return resources.stream().filter(Resource::isActive)
                    .filter(resource -> isAvailableLocked(resource, start, end)).toList();
        }
    }

    public void addResource(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("A resource is required.");
        }
        if (!resource.isActive()) {
            throw new IllegalArgumentException("Only active resources can be added to the catalog.");
        }
        refresh();
        synchronized (stateLock) {
            ensureNewResourceLocked(resource);
        }
        repository.addResource(resource);
        synchronized (stateLock) {
            resources.add(resource);
        }
    }

    public void retireResource(String resourceId) {
        refresh();
        Resource resource;
        synchronized (stateLock) {
            resource = requireActiveResourceLocked(resourceId);
        }

        if (!repository.retireResource(resource.getId(), LocalDateTime.now())) {
            throw new IllegalArgumentException("This resource has a current or future booking and cannot be retired.");
        }
        synchronized (stateLock) {
            resource.retire();
        }
    }

    public Reservation reserve(String resourceId, String borrowerName,
                               LocalDateTime start, LocalDateTime end) {
        // Validate dates and the name before checking existing reservations.
        if (borrowerName == null || borrowerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a borrower name.");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        if (!start.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reservation start time must be in the future.");
        }

        refresh();
        Resource resource;
        synchronized (stateLock) {
            resource = requireActiveResourceLocked(resourceId);
            if (!isAvailableLocked(resource, start, end)) {
                throw new IllegalArgumentException("That resource is already reserved for an overlapping time.");
            }
        }

        Reservation reservation = repository.createReservation(resource.getId(), borrowerName, start, end);
        synchronized (stateLock) {
            reservations.add(reservation);
        }
        return reservation;
    }

    public boolean isAvailable(String resourceId, LocalDateTime start, LocalDateTime end) {
        validateTimeRange(start, end);
        synchronized (stateLock) {
            Resource resource = requireActiveResourceLocked(resourceId);
            return isAvailableLocked(resource, start, end);
        }
    }

    public boolean cancel(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return false;
        }
        refresh();
        Reservation reservation;
        synchronized (stateLock) {
            reservation = reservations.stream()
                    .filter(item -> item.getId().equalsIgnoreCase(reservationId.trim()))
                    .findFirst().orElse(null);
            if (reservation == null) return false;
        }
        boolean cancelled = repository.cancelReservation(reservation.getId());
        synchronized (stateLock) {
            // An already-cancelled database row must not keep blocking this instance's cache.
            reservations.remove(reservation);
        }
        return cancelled;
    }

    private Optional<Resource> findResourceLocked(String resourceId) {
        if (resourceId == null) return Optional.empty();
        return resources.stream()
                .filter(resource -> resource.getId().equalsIgnoreCase(resourceId.trim()))
                .findFirst();
    }

    private Resource requireActiveResourceLocked(String resourceId) {
        Resource resource = findResourceLocked(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("No resource found with ID " + resourceId + "."));
        if (!resource.isActive()) {
            throw new IllegalArgumentException("That resource is no longer available for booking.");
        }
        return resource;
    }

    private void ensureNewResourceLocked(Resource resource) {
        if (findResourceLocked(resource.getId()).isPresent()) {
            throw new IllegalArgumentException("A resource with that ID already exists.");
        }
    }

    private boolean isAvailableLocked(Resource resource, LocalDateTime start, LocalDateTime end) {
        return reservations.stream().noneMatch(existing ->
                existing.getResourceId().equalsIgnoreCase(resource.getId())
                        && existing.overlaps(start, end));
    }

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
    }
}
