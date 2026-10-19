package campus.booking;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BookingServiceTest {
    private static int testsRun;

    public static void main(String[] args) {
        testReservationCanBeCreated();
        testOverlappingReservationIsRejected();
        testBackToBackReservationsAreAllowed();
        testDifferentResourcesCanBeReservedAtSameTime();
        testEndMustBeAfterStart();
        testUnknownResourceIsRejected();
        testInvalidBorrowerNameIsRejected();
        testAvailabilityUsesBookingOverlapRules();
        testAvailabilityFilterUsesBookingOverlapRules();
        testDuplicateResourceIdsAreRejectedIgnoringCase();
        testNullResourceEntryIsRejected();
        testCancellationMakesTimeAvailableAgain();
        testResourcesCanBeAddedAndSafelyRetired();
        testSeatingCapacityOnlyAppliesToRooms();
        testSampleCatalogHasMultipleBookAndEquipmentCopies();
        testSQLitePersistenceAndConflictConstraint();
        testConcurrentSQLiteBookingsCannotOverlap();
        System.out.println("Passed " + testsRun + " application checks.");
    }

    private static void testReservationCanBeCreated() {
        BookingService service = serviceWithOneRoom();
        Reservation reservation = service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        check("R0001".equals(reservation.getId()), "first reservation gets a reference");
        check(service.getReservations().size() == 1, "reservation is stored");
    }

    private static void testOverlappingReservationIsRejected() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        expectFailure(() -> service.reserve("C101", "Sam", time("2026-10-20 10:30"), time("2026-10-20 11:30")));
        check(service.getReservations().size() == 1, "rejected booking does not change reservations");
    }

    private static void testBackToBackReservationsAreAllowed() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        service.reserve("C101", "Sam", time("2026-10-20 11:00"), time("2026-10-20 12:00"));
        check(service.getReservations().size() == 2, "back-to-back reservations are allowed");
    }

    private static void testDifferentResourcesCanBeReservedAtSameTime() {
        BookingService service = new BookingService(List.of(
                new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30),
                new Resource("C102", "Classroom 102", ResourceType.CLASSROOM, "Main Building", 20)));
        service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        service.reserve("C102", "Sam", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        check(service.getReservations().size() == 2, "different resources can be booked at the same time");
    }

    private static void testEndMustBeAfterStart() {
        BookingService service = serviceWithOneRoom();
        expectFailure(() -> service.reserve("C101", "Alex", time("2026-10-20 11:00"), time("2026-10-20 10:00")));
        check(service.getReservations().isEmpty(), "invalid time range is not stored");
    }

    private static void testUnknownResourceIsRejected() {
        BookingService service = serviceWithOneRoom();
        expectFailure(() -> service.reserve("UNKNOWN", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00")));
    }

    private static void testInvalidBorrowerNameIsRejected() {
        BookingService service = serviceWithOneRoom();
        expectFailure(() -> service.reserve("C101", "  ", time("2026-10-20 10:00"), time("2026-10-20 11:00")));
        check(service.getReservations().isEmpty(), "blank borrower name is not stored");
    }

    private static void testAvailabilityUsesBookingOverlapRules() {
        BookingService service = serviceWithOneRoom();
        service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        check(!service.isAvailable("C101", time("2026-10-20 10:30"), time("2026-10-20 11:30")),
                "overlapping time is unavailable");
        check(service.isAvailable("C101", time("2026-10-20 11:00"), time("2026-10-20 12:00")),
                "back-to-back time is available");
    }

    private static void testAvailabilityFilterUsesBookingOverlapRules() {
        BookingService service = new BookingService(List.of(
                new Resource("C101", "Room 101", ResourceType.CLASSROOM, "Building A", 24),
                new Resource("C102", "Room 102", ResourceType.CLASSROOM, "Building A", 18)));
        service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        check(service.availableResources(time("2026-10-20 10:30"), time("2026-10-20 11:30"))
                        .stream().map(Resource::getId).toList().equals(List.of("C102")),
                "availability filtering excludes only the overlapping resource");
        check(service.availableResources(time("2026-10-20 11:00"), time("2026-10-20 12:00")).size() == 2,
                "availability filtering permits a booking at the previous end boundary");
        expectFailure(() -> service.availableResources(time("2026-10-20 12:00"), time("2026-10-20 11:00")));
    }

    private static void testDuplicateResourceIdsAreRejectedIgnoringCase() {
        expectFailure(() -> new BookingService(List.of(
                new Resource("C101", "Room 1", ResourceType.CLASSROOM, "Main Building", 20),
                new Resource("c101", "Room 2", ResourceType.CLASSROOM, "Main Building", 20))));
    }

    private static void testNullResourceEntryIsRejected() {
        List<Resource> resources = new java.util.ArrayList<>();
        resources.add(null);
        expectFailure(() -> new BookingService(resources));
    }

    private static void testCancellationMakesTimeAvailableAgain() {
        BookingService service = serviceWithOneRoom();
        Reservation reservation = service.reserve("C101", "Alex", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        check(service.cancel(reservation.getId()), "existing reservation can be cancelled");
        service.reserve("C101", "Sam", time("2026-10-20 10:00"), time("2026-10-20 11:00"));
        check(service.getReservations().size() == 1, "cancelled time can be reserved again");
    }

    private static void testResourcesCanBeAddedAndSafelyRetired() {
        BookingService service = serviceWithOneRoom();
        Resource equipment = new Resource("E014", "Audio recorder", ResourceType.EQUIPMENT, "Media desk", 0);
        service.addResource(equipment);
        check(service.getResources().size() == 2, "new resource appears in the active catalog");
        expectFailure(() -> service.addResource(
                new Resource("e014", "Duplicate recorder", ResourceType.EQUIPMENT, "Media desk", 0)));

        LocalDateTime start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        Reservation booking = service.reserve("C101", "Alex", start, start.plusHours(1));
        expectFailure(() -> service.retireResource("C101"));
        check(service.findResource("C101").orElseThrow().isActive(),
                "resource with an active booking remains active after failed retirement");
        check(service.cancel(booking.getId()), "reservation can be cancelled before retiring its resource");
        service.retireResource("C101");
        check(service.getResources().size() == 1, "retired resource is removed from the active catalog");
        check(service.findResource("C101").isPresent()
                        && !service.findResource("C101").orElseThrow().isActive(),
                "retirement preserves the resource record for reservation references");
        expectFailure(() -> service.reserve("C101", "Sam", start.plusHours(1), start.plusHours(2)));
    }

    private static void testSeatingCapacityOnlyAppliesToRooms() {
        Resource book = new Resource("B001", "Book", ResourceType.BOOK, "Library", 0);
        Resource equipment = new Resource("E001", "Laptop", ResourceType.EQUIPMENT, "Desk", 0);
        Resource room = new Resource("C101", "Room", ResourceType.CLASSROOM, "Building A", 24);
        check(book.getSeatingCapacity() == 0 && !book.getType().hasSeatingCapacity(),
                "books do not report a seat count");
        check(equipment.getSeatingCapacity() == 0 && !equipment.getType().hasSeatingCapacity(),
                "equipment does not report a seat count");
        check(room.getSeatingCapacity() == 24 && room.getType().hasSeatingCapacity(),
                "rooms retain their seat count");
        expectFailure(() -> new Resource("B002", "Book", ResourceType.BOOK, "Library", 1));
        expectFailure(() -> new Resource("C102", "Room", ResourceType.CLASSROOM, "Building A", 0));
    }

    private static void testSampleCatalogHasMultipleBookAndEquipmentCopies() {
        List<Resource> resources = SampleData.createResources();
        long books = resources.stream().filter(resource -> resource.getType() == ResourceType.BOOK).count();
        long equipment = resources.stream().filter(resource -> resource.getType() == ResourceType.EQUIPMENT).count();
        check(resources.size() == 70, "sample catalog has the expected 70 individual resources");
        check(books == 28, "sample catalog includes 28 individually bookable book copies");
        check(equipment == 20, "sample catalog includes 20 individually bookable equipment items");
        check("—".equals(new ResourceTableModel(resources).getValueAt(0, 4)),
                "the resource table shows that books have no seating capacity");
    }

    private static void testSQLitePersistenceAndConflictConstraint() {
        Path database = null;
        Path folder = null;
        try {
            folder = Files.createTempDirectory("booking-sqlite-test-");
            database = folder.resolve("bookings.db");
            List<Resource> catalog = List.of(
                    new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30));
            SQLiteBookingRepository repository = new SQLiteBookingRepository(database);
            repository.initialize(catalog);

            LocalDateTime start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
            Reservation first = repository.createReservation("C101", "Alex", start, start.plusHours(1));
            check("R0001".equals(first.getId()), "first database reservation gets the expected reference");
            expectFailure(() -> repository.createReservation("C101", "Sam",
                    start.plusMinutes(30), start.plusHours(2)));
            Reservation backToBack = repository.createReservation("C101", "Sam",
                    start.plusHours(1), start.plusHours(2));
            check("R0002".equals(backToBack.getId()), "a rejected transaction does not consume a reservation number");

            SQLiteBookingRepository reopened = new SQLiteBookingRepository(database);
            reopened.initialize(catalog);
            check(reopened.loadReservations().size() == 2, "reservations reload after reopening the database");
            check(reopened.loadResources().size() == 1, "the initial catalog is not duplicated on restart");
            check(reopened.cancelReservation(first.getId()), "a saved reservation can be cancelled in SQLite");
            check(reopened.loadReservations().size() == 1, "cancelled reservations are excluded from active bookings");

            BookingService service = new BookingService(
                    reopened.loadResources(), reopened.loadReservations(), reopened);
            expectFailure(() -> service.retireResource("C101"));
            check(service.getResources().get(0).isActive(), "a resource with a future booking stays active");
            check(service.cancel(backToBack.getId()), "the persisted booking can be cancelled through the service");
            Resource equipment = new Resource("E001", "Portable speaker", ResourceType.EQUIPMENT, "Media desk", 0);
            service.addResource(equipment);
            check(reopened.loadResources().size() == 2, "inventory additions persist in SQLite");
            service.retireResource("C101");

            Reservation afterRestart = reopened.createReservation("E001", "Taylor", start, start.plusHours(1));
            check("R0003".equals(afterRestart.getId()), "reservation numbers remain unique after cancellation");
            check(reopened.cancelReservation(afterRestart.getId()), "the additional persisted booking can be cancelled");
            SQLiteBookingRepository afterRetirement = new SQLiteBookingRepository(database);
            afterRetirement.initialize(catalog);
            check(afterRetirement.loadResources().size() == 2
                            && afterRetirement.loadResources().stream()
                            .filter(resource -> resource.getId().equals("C101"))
                            .noneMatch(Resource::isActive),
                    "retired resources stay in the database but are inactive after restart");
            check(afterRetirement.loadReservations().isEmpty(), "cancelled bookings stay out of the active list after restart");
        } catch (Exception exception) {
            throw new AssertionError("SQLite persistence checks could not complete.", exception);
        } finally {
            try {
                if (database != null) {
                    Files.deleteIfExists(Path.of(database + "-shm"));
                    Files.deleteIfExists(Path.of(database + "-wal"));
                    Files.deleteIfExists(database);
                }
                if (folder != null) Files.deleteIfExists(folder);
            } catch (Exception exception) {
                throw new AssertionError("Could not remove the temporary test database.", exception);
            }
        }
    }

    private static void testConcurrentSQLiteBookingsCannotOverlap() {
        Path folder = null;
        Path database = null;
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            folder = Files.createTempDirectory("booking-concurrency-test-");
            database = folder.resolve("bookings.db");
            List<Resource> catalog = List.of(
                    new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30));
            SQLiteBookingRepository firstRepository = new SQLiteBookingRepository(database);
            firstRepository.initialize(catalog);
            SQLiteBookingRepository secondRepository = new SQLiteBookingRepository(database);
            BookingService firstService = new BookingService(
                    firstRepository.loadResources(), firstRepository.loadReservations(), firstRepository);
            BookingService secondService = new BookingService(
                    secondRepository.loadResources(), secondRepository.loadReservations(), secondRepository);

            CountDownLatch startTogether = new CountDownLatch(1);
            LocalDateTime start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
            Future<Boolean> first = workers.submit(() -> attemptReservation(
                    firstService, startTogether, "Alex", start));
            Future<Boolean> second = workers.submit(() -> attemptReservation(
                    secondService, startTogether, "Sam", start));
            startTogether.countDown();

            boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);
            check(firstSucceeded != secondSucceeded,
                    "only one simultaneous overlapping reservation can succeed");
            check(firstRepository.loadReservations().size() == 1,
                    "SQLite stores exactly one of the simultaneous overlapping reservations");
        } catch (Exception exception) {
            throw new AssertionError("Concurrent SQLite booking checks could not complete.", exception);
        } finally {
            workers.shutdownNow();
            try {
                if (database != null) {
                    Files.deleteIfExists(Path.of(database + "-shm"));
                    Files.deleteIfExists(Path.of(database + "-wal"));
                    Files.deleteIfExists(database);
                }
                if (folder != null) Files.deleteIfExists(folder);
            } catch (Exception exception) {
                throw new AssertionError("Could not remove the temporary concurrency-test database.", exception);
            }
        }
    }

    private static boolean attemptReservation(BookingService service, CountDownLatch startTogether,
                                              String borrower, LocalDateTime start) {
        try {
            startTogether.await();
            service.reserve("C101", borrower, start, start.plusHours(1));
            return true;
        } catch (IllegalArgumentException expectedConflict) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent reservation worker was interrupted.", exception);
        }
    }

    private static BookingService serviceWithOneRoom() {
        return new BookingService(List.of(
                new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Main Building", 30)));
    }

    private static LocalDateTime time(String value) {
        return LocalDateTime.parse(value.replace(' ', 'T') + ":00");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected the operation to be rejected.");
        } catch (IllegalArgumentException expected) {
            testsRun++;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        testsRun++;
    }
}
