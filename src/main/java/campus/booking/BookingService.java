package campus.booking;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class BookingService {
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
        return resources.stream().filter(Resource::isActive).toList();
    }

    public List<Reservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }

    public Optional<Resource> findResource(String resourceId) {
        if (resourceId == null) {
            return Optional.empty();
        }
        return resources.stream()
                .filter(resource -> resource.getId().equalsIgnoreCase(resourceId.trim()))
                .findFirst();
    }

    public List<Resource> availableResources(LocalDateTime start, LocalDateTime end) {
        validateTimeRange(start, end);
        return getResources().stream()
                .filter(resource -> isAvailable(resource.getId(), start, end))
                .toList();
    }

    public void addResource(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("A resource is required.");
        }
        if (!resource.isActive()) {
            throw new IllegalArgumentException("Only active resources can be added to the catalog.");
        }
        if (findResource(resource.getId()).isPresent()) {
            throw new IllegalArgumentException("A resource with that ID already exists.");
        }
        if (repository != null) {
            repository.addResource(resource);
        }
        resources.add(resource);
    }

    public void retireResource(String resourceId) {
        Resource resource = requireActiveResource(resourceId);
        LocalDateTime now = LocalDateTime.now();
        boolean hasActiveBooking = reservations.stream()
                .anyMatch(reservation -> reservation.getResourceId().equalsIgnoreCase(resource.getId())
                        && reservation.getEnd().isAfter(now));
        if (hasActiveBooking) {
            throw new IllegalArgumentException("This resource has a current or future booking and cannot be retired.");
        }
        if (repository != null && !repository.retireResource(resource.getId(), now)) {
            throw new IllegalArgumentException("This resource has a current or future booking and cannot be retired.");
        }
        resource.retire();
    }

    public Reservation reserve(String resourceId, String borrowerName,
                               LocalDateTime start, LocalDateTime end) {
        Resource resource = requireActiveResource(resourceId);

        // Validate dates and the name before checking existing reservations.
        if (borrowerName == null || borrowerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a borrower name.");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        if (!isAvailable(resource.getId(), start, end)) {
            throw new IllegalArgumentException("That resource is already reserved for an overlapping time.");
        }

        Reservation reservation;
        if (repository != null) {
            reservation = repository.createReservation(resource.getId(), borrowerName, start, end);
        } else {
            String reservationId = String.format("R%04d", nextReservationNumber++);
            reservation = new Reservation(reservationId, resource.getId(), borrowerName, start, end);
        }
        reservations.add(reservation);
        return reservation;
    }

    public boolean isAvailable(String resourceId, LocalDateTime start, LocalDateTime end) {
        validateTimeRange(start, end);
        Resource resource = requireActiveResource(resourceId);

        return reservations.stream().noneMatch(existing ->
                existing.getResourceId().equalsIgnoreCase(resource.getId())
                        && existing.overlaps(start, end));
    }

    public boolean cancel(String reservationId) {
        if (reservationId == null || reservationId.trim().isEmpty()) {
            return false;
        }
        Reservation reservation = reservations.stream()
                .filter(item -> item.getId().equalsIgnoreCase(reservationId.trim()))
                .findFirst().orElse(null);
        if (reservation == null) {
            return false;
        }
        if (repository != null && !repository.cancelReservation(reservation.getId())) {
            return false;
        }
        return reservations.remove(reservation);
    }

    private Resource requireActiveResource(String resourceId) {
        Resource resource = findResource(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("No resource found with ID " + resourceId + "."));
        if (!resource.isActive()) {
            throw new IllegalArgumentException("That resource is no longer available for booking.");
        }
        return resource;
    }

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
    }
}
