# Campus Resource Booking

A Java desktop app for browsing a shared catalog and reserving rooms, books, and equipment. It uses Swing for the interface and a local SQLite database for saved inventory and bookings.

## Features

1. **Transactional SQLite persistence:** reservations are created in a transaction, while database constraints and triggers reject invalid or overlapping bookings.
2. **Consistent booking conflict rules:** overlapping reservations are rejected, adjacent bookings are allowed, and new reservations must start in the future.
3. **Time-based resource filtering and safe inventory edits:** find resources for a chosen slot, add inventory, and retire items only when they have no active bookings.
4. **Practical desktop workflows:** browse and search the catalog, cancel bookings, and select dates and times from a click-based picker.

The included sample catalog is generic example data. It does not represent a real organization or inventory.
The bundled cat artwork is used for the application and date-picker icons; the Windows `.ico` is available for shortcuts.

## Design

`Resource` and `Reservation` represent the core data. `BookingService` validates booking, availability, and inventory operations. `BookingRepository` defines the storage operations and `SQLiteBookingRepository` implements them with JDBC. Reservation creation allocates its reference and inserts the booking in one transaction; SQLite constraints and triggers also reject invalid or overlapping rows at the database boundary. Resources are retired with an active flag, and cancelled bookings are marked cancelled, preserving their records and resource references.

`BookingFrame` handles the Swing workflows and adapts service results to the resource and reservation table models. `DateTimePickerDialog` provides date and time selection. The interface uses Swing layout managers and split panes so the catalog, booking form, and reservation table resize with the window.

`Main` initializes the database and loads its data in a `SwingWorker`, then creates the interface on Swing's Event Dispatch Thread (EDT). Reservation, cancellation, inventory-add, and retirement writes also run in background workers; their completion handlers update Swing components on the EDT. The service protects its in-memory collections with short synchronized sections and does not hold that lock while JDBC is working.

User-provided SQL values are bound with `PreparedStatement`. Reservation creation updates the reference sequence and inserts the booking in one transaction; a SQLite trigger rejects overlapping active reservations at the database boundary. The availability filter uses the application's loaded data for a quick response, while the database remains authoritative when a reservation is saved.

## Technology and setup

- JDK 17 or newer (`java` and `javac` on `PATH`)
- Windows for the included batch scripts
- Xerial SQLite JDBC 3.53.4.0 is bundled in `lib/`; its license files are alongside the JAR

Build and run from this folder in PowerShell:

```powershell
.\build.bat
.\run.bat
```

To run the application directly after building:

```powershell
java -cp "out;lib/*" campus.booking.Main
```

The database is stored at `%USERPROFILE%\.resource-booking\bookings.db`. It stays local to this Windows user account.

## Tests

Run the included behavior and SQLite integration checks:

```powershell
.\test.bat
```

The test harness covers normal reservations, past-time and invalid-input rejection, conflicts and interval boundaries, availability filtering, cancellation, adding and retiring inventory, SQLite persistence across repository reopen, unique in-memory references, and overlapping bookings attempted concurrently.

## Limitations

- This is a local desktop application; it does not synchronize data between computers or user accounts.
- It does not implement accounts, permissions, organization-specific opening hours, or resource-specific loan periods.
- Retired resources and cancelled bookings remain in SQLite for reference, but the interface lists only active resources and bookings.

The SQLite JDBC driver is provided by [Xerial](https://github.com/xerial/sqlite-jdbc) and is distributed with its upstream license texts in `lib/`.
