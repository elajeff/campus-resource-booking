package campus.booking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class BookingService {
    private final Object stateLock = new Object();
    private final List<Resource> resources;
    private final List<Reservation> reservations = new ArrayList<>();
    private final BookingRepository repository;
    private int nextReservationNumber = 1;

    public BookingService(List<Resource> resources) {
        this(resources, List.of(), null);
    }

    public BookingService(List<Resource> resources, List<Reservation> reservations, BookingRepository repository) {
        if (resources == null) {
            throw new IllegalArgumentException("Resource list is required.");
        }
        if (reservations == null) {
            throw new IllegalArgumentException("Reservation list is required.");
        }
        this.resources = new ArrayList<>(resources);
        this.reservations.addAll(reservations);
        this.repository = repository;
        checkForDuplicateResourceIds();
    }

    private void checkForDuplicateResourceIds() {
        Set<String> resourceIds = new HashSet<>();
        for (Resource resource : resources) {
            if (resource == null) {
                throw new IllegalArgumentException("Resource list cannot contain null entries.");
            }
            String normalizedId = resource.getId().toLowerCase(Locale.ROOT);
            if (!resourceIds.add(normalizedId)) {
                throw new IllegalArgumentException("Duplicate resource ID: " + resource.getId());
            }
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
        if (repository == null) {
            synchronized (stateLock) {
                ensureNewResourceLocked(resource);
                resources.add(resource);
            }
            return;
        }

        synchronized (stateLock) {
            ensureNewResourceLocked(resource);
        }
        repository.addResource(resource);
        synchronized (stateLock) {
            resources.add(resource);
        }
    }

    public void retireResource(String resourceId) {
        Resource resource;
        LocalDateTime now = LocalDateTime.now();
        synchronized (stateLock) {
            resource = requireActiveResourceLocked(resourceId);
            if (hasActiveBookingLocked(resource, now)) {
                throw new IllegalArgumentException(
                        "This resource has a current or future booking and cannot be retired.");
            }
            if (repository == null) {
                resource.retire();
                return;
            }
        }

        if (!repository.retireResource(resource.getId(), now)) {
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

        Resource resource;
        synchronized (stateLock) {
            resource = requireActiveResourceLocked(resourceId);
            if (!isAvailableLocked(resource, start, end)) {
                throw new IllegalArgumentException("That resource is already reserved for an overlapping time.");
            }
            if (repository == null) {
                String reservationId = nextReservationIdLocked();
                Reservation reservation = new Reservation(reservationId, resource.getId(), borrowerName, start, end);
                reservations.add(reservation);
                return reservation;
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
        Reservation reservation;
        synchronized (stateLock) {
            reservation = reservations.stream()
                    .filter(item -> item.getId().equalsIgnoreCase(reservationId.trim()))
                    .findFirst().orElse(null);
            if (reservation == null) return false;
            if (repository == null) return reservations.remove(reservation);
        }
        if (!repository.cancelReservation(reservation.getId())) {
            return false;
        }
        synchronized (stateLock) {
            return reservations.remove(reservation);
        }
    }

    private String nextReservationIdLocked() {
        while (true) {
            String candidate = String.format("R%04d", nextReservationNumber++);
            boolean alreadyUsed = false;
            for (Reservation existing : reservations) {
                if (existing.getId().equalsIgnoreCase(candidate)) {
                    alreadyUsed = true;
                    break;
                }
            }
            if (!alreadyUsed) {
                return candidate;
            }
        }
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

    private boolean hasActiveBookingLocked(Resource resource, LocalDateTime now) {
        return reservations.stream().anyMatch(reservation ->
                reservation.getResourceId().equalsIgnoreCase(resource.getId())
                        && reservation.getEnd().isAfter(now));
    }

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
    }
}
