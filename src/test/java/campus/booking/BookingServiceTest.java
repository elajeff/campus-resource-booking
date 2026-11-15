package campus.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BookingServiceTest {
    @TempDir
    Path folder;

    @Test
    void reservationCanBeCreated() {
        BookingService service = serviceWithOneRoom();
        Reservation reservation = service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        assertEquals("R0001", reservation.getId());
        assertEquals(1, service.getReservations().size());
    }

    @Test
    void pastReservationStartIsRejected() {
        BookingService service = serviceWithOneRoom();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("C101", "Alex", start, start.plusHours(1)));
        assertTrue(service.getReservations().isEmpty());
    }

    @Test
    void overlappingReservationIsRejected() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("C101", "Sam", time("10:30"), time("11:30")));
        assertEquals(1, service.getReservations().size());
    }

    @Test
    void backToBackReservationsAreAllowed() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        service.reserve("C101", "Sam", time("11:00"), time("12:00"));
        assertEquals(2, service.getReservations().size());
    }

    @Test
    void differentResourcesCanBeReservedAtSameTime() {
        BookingService service = service(
                new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30),
                new Resource("C102", "Classroom 102", ResourceType.CLASSROOM, "Main Building", 20));
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        service.reserve("C102", "Sam", time("10:00"), time("11:00"));
        assertEquals(2, service.getReservations().size());
    }

    @Test
    void endMustBeAfterStart() {
        BookingService service = serviceWithOneRoom();
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("C101", "Alex", time("11:00"), time("10:00")));
        assertTrue(service.getReservations().isEmpty());
    }

    @Test
    void unknownResourceIsRejected() {
        BookingService service = serviceWithOneRoom();
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("UNKNOWN", "Alex", time("10:00"), time("11:00")));
    }

    @Test
    void blankBorrowerNameIsRejected() {
        BookingService service = serviceWithOneRoom();
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("C101", "  ", time("10:00"), time("11:00")));
        assertTrue(service.getReservations().isEmpty());
    }

    @Test
    void availabilityUsesBookingOverlapRules() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        assertFalse(service.isAvailable("C101", time("10:30"), time("11:30")));
        assertTrue(service.isAvailable("C101", time("11:00"), time("12:00")));
    }

    @Test
    void availabilityFilterUsesBookingOverlapRules() {
        BookingService service = service(
                new Resource("C101", "Room 101", ResourceType.CLASSROOM, "Building A", 24),
                new Resource("C102", "Room 102", ResourceType.CLASSROOM, "Building A", 18));
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        assertEquals(List.of("C102"), service.availableResources(time("10:30"), time("11:30"))
                .stream().map(Resource::getId).toList());
        assertEquals(2, service.availableResources(time("11:00"), time("12:00")).size());
        assertThrows(IllegalArgumentException.class,
                () -> service.availableResources(time("12:00"), time("11:00")));
    }

    @Test
    void cancellationMakesTimeAvailableAgain() {
        BookingService service = serviceWithOneRoom();
        Reservation reservation = service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        assertTrue(service.cancel(reservation.getId()));
        service.reserve("C101", "Sam", time("10:00"), time("11:00"));
        assertEquals(1, service.getReservations().size());
    }

    @Test
    void resourcesCanBeAddedAndSafelyRetired() {
        BookingService service = serviceWithOneRoom();
        Resource equipment = new Resource("E014", "Audio recorder", ResourceType.EQUIPMENT, "Media desk", 0);
        service.addResource(equipment);
        assertEquals(2, service.getResources().size());
        assertThrows(IllegalArgumentException.class, () -> service.addResource(
                new Resource("e014", "Duplicate recorder", ResourceType.EQUIPMENT, "Media desk", 0)));

        LocalDateTime start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        Reservation booking = service.reserve("C101", "Alex", start, start.plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> service.retireResource("C101"));
        assertTrue(service.findResource("C101").orElseThrow().isActive());
        assertTrue(service.cancel(booking.getId()));
        service.retireResource("C101");
        assertEquals(1, service.getResources().size());
        assertFalse(service.findResource("C101").orElseThrow().isActive());
        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("C101", "Sam", start.plusHours(1), start.plusHours(2)));
    }

    @Test
    void seatingCapacityOnlyAppliesToRooms() {
        Resource book = new Resource("B001", "Book", ResourceType.BOOK, "Library", 0);
        Resource equipment = new Resource("E001", "Laptop", ResourceType.EQUIPMENT, "Desk", 0);
        Resource room = new Resource("C101", "Room", ResourceType.CLASSROOM, "Building A", 24);
        assertFalse(book.getType().hasSeatingCapacity());
        assertFalse(equipment.getType().hasSeatingCapacity());
        assertEquals(24, room.getSeatingCapacity());
        assertTrue(room.getType().hasSeatingCapacity());
        assertThrows(IllegalArgumentException.class,
                () -> new Resource("B002", "Book", ResourceType.BOOK, "Library", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Resource("C102", "Room", ResourceType.CLASSROOM, "Building A", 0));
    }

    @Test
    void databaseRejectsAnOverlappingInsert() {
        SQLiteBookingRepository repository = new SQLiteBookingRepository(folder.resolve("bookings.db"));
        repository.initialize(List.of(
                new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30)));
        LocalDateTime start = time("10:00");
        assertEquals("R0001", repository.createReservation("C101", "Alex", start, start.plusHours(1)).getId());
        assertThrows(IllegalArgumentException.class,
                () -> repository.createReservation("C101", "Sam", start.plusMinutes(30), start.plusHours(2)));
        assertEquals("R0002", repository.createReservation("C101", "Sam", start.plusHours(1), start.plusHours(2))
                .getId(), "a rejected insert does not use up a number");
    }

    @Test
    void bookingsAndInventorySurviveReopening() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        service.addResource(new Resource("E001", "Portable speaker", ResourceType.EQUIPMENT, "Media desk", 0));

        BookingService reopened = serviceWithOneRoom();
        assertEquals(1, reopened.getReservations().size());
        assertEquals(2, reopened.getResources().size(), "the starting catalog is not added again");
    }

    @Test
    void cancelledBookingsAndRetiredResourcesStayHiddenAfterReopening() {
        BookingService service = serviceWithOneRoom();
        Reservation booking = service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        service.cancel(booking.getId());
        service.retireResource("C101");

        BookingService reopened = serviceWithOneRoom();
        assertTrue(reopened.getReservations().isEmpty());
        assertTrue(reopened.getResources().isEmpty());
        assertFalse(reopened.findResource("C101").orElseThrow().isActive());
    }

    @Test
    void reservationNumbersAreNotReusedAfterCancelling() {
        BookingService service = serviceWithOneRoom();
        Reservation first = service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        service.cancel(first.getId());
        assertEquals("R0002", service.reserve("C101", "Sam", time("10:00"), time("11:00")).getId());
    }

    @Test
    void concurrentBookingsCannotOverlap() throws Exception {
        BookingService firstService = serviceWithOneRoom();
        BookingService secondService = serviceWithOneRoom();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch startTogether = new CountDownLatch(1);
            LocalDateTime start = time("10:00");
            Future<Boolean> first = workers.submit(() -> attemptReservation(
                    firstService, startTogether, "Alex", start));
            Future<Boolean> second = workers.submit(() -> attemptReservation(
                    secondService, startTogether, "Sam", start));
            startTogether.countDown();

            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS),
                    "only one of two overlapping bookings made at once succeeds");
            assertEquals(1, serviceWithOneRoom().getReservations().size());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void slotCancelledInAnotherWindowCanBeBooked() {
        BookingService first = serviceWithOneRoom();
        BookingService second = serviceWithOneRoom();
        Reservation original = first.reserve("C101", "Alex", time("10:00"), time("11:00"));

        first.cancel(original.getId());
        second.reserve("C101", "Sam", time("10:00"), time("11:00"));
        assertEquals(1, second.getReservations().size());
    }

    @Test
    void cancellingABookingAlreadyCancelledElsewhereReturnsFalse() {
        BookingService first = serviceWithOneRoom();
        Reservation booking = first.reserve("C101", "Alex", time("10:00"), time("11:00"));
        BookingService second = serviceWithOneRoom();

        first.cancel(booking.getId());
        assertFalse(second.cancel(booking.getId()));
        assertTrue(second.getReservations().isEmpty());
        assertTrue(second.isAvailable("C101", time("10:00"), time("11:00")));
    }

    @Test
    void refreshLoadsChangesFromAnotherWindow() {
        BookingService first = serviceWithOneRoom();
        BookingService second = serviceWithOneRoom();
        first.reserve("C101", "Alex", time("10:00"), time("11:00"));
        first.addResource(new Resource("E001", "Camera", ResourceType.EQUIPMENT, "Desk", 0));

        second.refresh();
        assertFalse(second.isAvailable("C101", time("10:00"), time("11:00")));
        assertEquals(2, second.getResources().size());

        first.retireResource("E001");
        second.refresh();
        assertEquals(1, second.getResources().size());
    }

    @Test
    void failedRefreshKeepsTheLastLoadedData() throws Exception {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("10:00"), time("11:00"));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("bookings.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE reservations");
        }

        assertThrows(BookingPersistenceException.class, service::refresh);
        assertEquals(1, service.getReservations().size());
        assertEquals(1, service.getResources().size());
    }

    private static boolean attemptReservation(BookingService service, CountDownLatch startTogether,
                                              String borrower, LocalDateTime start) throws InterruptedException {
        startTogether.await();
        try {
            service.reserve("C101", borrower, start, start.plusHours(1));
            return true;
        } catch (IllegalArgumentException expectedConflict) {
            return false;
        }
    }

    private BookingService service(Resource... catalog) {
        SQLiteBookingRepository repository = new SQLiteBookingRepository(folder.resolve("bookings.db"));
        repository.initialize(List.of(catalog));
        return new BookingService(repository);
    }

    private BookingService serviceWithOneRoom() {
        return service(new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30));
    }

    private static LocalDateTime time(String value) {
        // Use a relative date so these tests still work months or years later.
        return LocalDate.now().plusDays(7).atTime(LocalTime.parse(value));
    }
}
