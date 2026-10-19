package campus.booking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Stores resources and reservations in a local SQLite database. */
public final class SQLiteBookingRepository implements BookingRepository {
    private static final DateTimeFormatter DATABASE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS");

    private final String jdbcUrl;

    public SQLiteBookingRepository(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("Database path is required.");
        }
        try {
            Path absolutePath = databasePath.toAbsolutePath();
            Files.createDirectories(absolutePath.getParent());
            this.jdbcUrl = "jdbc:sqlite:" + absolutePath;
        } catch (Exception exception) {
            throw new BookingPersistenceException("Could not prepare the booking database folder.", exception);
        }
    }

    /** Creates the schema and seeds the example catalog only for a new, empty database. */
    public void initialize(List<Resource> sampleResources) {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS resources (
                        id TEXT PRIMARY KEY COLLATE NOCASE,
                        name TEXT NOT NULL CHECK (length(trim(name)) > 0),
                        type TEXT NOT NULL CHECK (type IN ('BOOK', 'CLASSROOM', 'LAB', 'STUDY_ROOM', 'EQUIPMENT')),
                        location TEXT NOT NULL CHECK (length(trim(location)) > 0),
                        seating_capacity INTEGER NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                        CHECK (
                            (type IN ('CLASSROOM', 'LAB', 'STUDY_ROOM') AND seating_capacity >= 1)
                            OR (type IN ('BOOK', 'EQUIPMENT') AND seating_capacity = 0)
                        )
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS reservations (
                        id TEXT PRIMARY KEY,
                        resource_id TEXT NOT NULL REFERENCES resources(id) ON DELETE RESTRICT,
                        borrower_name TEXT NOT NULL CHECK (length(trim(borrower_name)) > 0),
                        start_time TEXT NOT NULL,
                        end_time TEXT NOT NULL,
                        cancelled INTEGER NOT NULL DEFAULT 0 CHECK (cancelled IN (0, 1)),
                        CHECK (end_time > start_time)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_sequence (
                        name TEXT PRIMARY KEY,
                        value INTEGER NOT NULL CHECK (value >= 0)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS reservations_resource_time "
                    + "ON reservations(resource_id, start_time, end_time)");
            statement.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS reservations_no_overlap_insert
                    BEFORE INSERT ON reservations
                    WHEN EXISTS (
                        SELECT 1 FROM reservations existing
                        WHERE existing.resource_id = NEW.resource_id COLLATE NOCASE
                          AND existing.cancelled = 0
                          AND existing.start_time < NEW.end_time
                          AND existing.end_time > NEW.start_time
                    )
                    BEGIN
                        SELECT RAISE(ABORT, 'reservation overlaps existing booking');
                    END
                    """);
            statement.executeUpdate("""
                    CREATE TRIGGER IF NOT EXISTS reservations_require_active_resource
                    BEFORE INSERT ON reservations
                    WHEN NOT EXISTS (
                        SELECT 1 FROM resources WHERE id = NEW.resource_id COLLATE NOCASE AND active = 1
                    )
                    BEGIN
                        SELECT RAISE(ABORT, 'resource is retired');
                    END
                    """);
            statement.executeUpdate("INSERT OR IGNORE INTO app_sequence(name, value) VALUES ('reservation', 0)");
            statement.executeUpdate("""
                    UPDATE app_sequence
                    SET value = MAX(value, COALESCE((
                        SELECT MAX(CAST(SUBSTR(id, 2) AS INTEGER)) FROM reservations
                    ), 0))
                    WHERE name = 'reservation'
                    """);
            statement.execute("PRAGMA user_version = 1");
        } catch (SQLException exception) {
            throw persistenceFailure("Could not initialize the booking database.", exception);
        }

        if (loadResources().isEmpty()) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (countResources(connection) == 0) {
                        for (Resource resource : sampleResources) {
                            insertResource(connection, resource);
                        }
                    }
                    connection.commit();
                } catch (Exception exception) {
                    rollback(connection, exception);
                    throw exception;
                }
            } catch (SQLException exception) {
                throw persistenceFailure("Could not load the initial resource catalog.", exception);
            }
        }
    }

    @Override
    public List<Resource> loadResources() {
        List<Resource> resources = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, type, location, seating_capacity, active FROM resources ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                resources.add(new Resource(result.getString("id"), result.getString("name"),
                        ResourceType.valueOf(result.getString("type")), result.getString("location"),
                        result.getInt("seating_capacity"), result.getInt("active") == 1));
            }
            return resources;
        } catch (SQLException exception) {
            throw persistenceFailure("Could not load resources.", exception);
        }
    }

    @Override
    public List<Reservation> loadReservations() {
        List<Reservation> reservations = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, resource_id, borrower_name, start_time, end_time
                     FROM reservations WHERE cancelled = 0 ORDER BY start_time, id
                     """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                reservations.add(new Reservation(result.getString("id"), result.getString("resource_id"),
                        result.getString("borrower_name"), parseTime(result.getString("start_time")),
                        parseTime(result.getString("end_time"))));
            }
            return reservations;
        } catch (SQLException exception) {
            throw persistenceFailure("Could not load reservations.", exception);
        }
    }

    @Override
    public void addResource(Resource resource) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                insertResource(connection, resource);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                if (isConstraint(exception)) {
                    throw new IllegalArgumentException("A resource with that ID already exists.", exception);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure("Could not save the resource.", exception);
        }
    }

    @Override
    public Reservation createReservation(String resourceId, String borrowerName,
                                         LocalDateTime start, LocalDateTime end) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                int nextNumber;
                try (PreparedStatement increment = connection.prepareStatement(
                        "UPDATE app_sequence SET value = value + 1 WHERE name = 'reservation'")) {
                    increment.executeUpdate();
                }
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT value FROM app_sequence WHERE name = 'reservation'");
                     ResultSet result = query.executeQuery()) {
                    if (!result.next()) throw new SQLException("Reservation sequence is missing.");
                    nextNumber = result.getInt(1);
                }
                Reservation reservation = new Reservation(String.format("R%04d", nextNumber),
                        resourceId, borrowerName, start, end);
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO reservations(id, resource_id, borrower_name, start_time, end_time)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    insert.setString(1, reservation.getId());
                    insert.setString(2, reservation.getResourceId());
                    insert.setString(3, reservation.getBorrowerName());
                    insert.setString(4, formatTime(reservation.getStart()));
                    insert.setString(5, formatTime(reservation.getEnd()));
                    insert.executeUpdate();
                }
                connection.commit();
                return reservation;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException && isOverlap(sqlException)) {
                    throw new IllegalArgumentException(
                            "That resource is already reserved for an overlapping time.", sqlException);
                }
                if (exception instanceof SQLException sqlException && isRetired(sqlException)) {
                    throw new IllegalArgumentException(
                            "That resource is no longer available for booking.", sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure("Could not save the reservation.", exception);
        }
    }

    @Override
    public boolean cancelReservation(String reservationId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE reservations SET cancelled = 1 WHERE id = ? AND cancelled = 0")) {
            statement.setString(1, reservationId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistenceFailure("Could not cancel the reservation.", exception);
        }
    }

    @Override
    public boolean retireResource(String resourceId, LocalDateTime activeAfter) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE resources SET active = 0
                    WHERE id = ? AND active = 1
                      AND NOT EXISTS (
                          SELECT 1 FROM reservations
                          WHERE resource_id = ? COLLATE NOCASE AND cancelled = 0 AND end_time > ?
                      )
                    """)) {
                statement.setString(1, resourceId);
                statement.setString(2, resourceId);
                statement.setString(3, formatTime(activeAfter));
                boolean retired = statement.executeUpdate() == 1;
                connection.commit();
                return retired;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw persistenceFailure("Could not retire the resource.", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private static void insertResource(Connection connection, Resource resource) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO resources(id, name, type, location, seating_capacity, active)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, resource.getId());
            statement.setString(2, resource.getName());
            statement.setString(3, resource.getType().name());
            statement.setString(4, resource.getLocation());
            statement.setInt(5, resource.getSeatingCapacity());
            statement.setInt(6, resource.isActive() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private static int countResources(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM resources")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String formatTime(LocalDateTime time) {
        return DATABASE_TIME.format(time);
    }

    private static LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value);
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static boolean isConstraint(SQLException exception) {
        return exception.getErrorCode() == 19 || exception.getMessage().toLowerCase().contains("constraint");
    }

    private static boolean isOverlap(SQLException exception) {
        return exception.getMessage() != null
                && exception.getMessage().contains("reservation overlaps existing booking");
    }

    private static boolean isRetired(SQLException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("resource is retired");
    }

    private static BookingPersistenceException persistenceFailure(String message, SQLException cause) {
        return new BookingPersistenceException(message + " " + cause.getMessage(), cause);
    }
}
