package campus.booking;

public class Resource {
    private final String id;
    private final String name;
    private final ResourceType type;
    private final String location;
    private final int seatingCapacity;
    private boolean active;

    public Resource(String id, String name, ResourceType type, String location, int seatingCapacity) {
        this(id, name, type, location, seatingCapacity, true);
    }

    Resource(String id, String name, ResourceType type, String location, int seatingCapacity, boolean active) {
        if (isBlank(id) || isBlank(name) || isBlank(location)) {
            throw new IllegalArgumentException("Resource ID, name, and location are required.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Resource type is required.");
        }
        if (type.hasSeatingCapacity() && seatingCapacity < 1) {
            throw new IllegalArgumentException("Room seating capacity must be at least 1.");
        }
        if (!type.hasSeatingCapacity() && seatingCapacity != 0) {
            throw new IllegalArgumentException("Books and equipment do not have a seating capacity.");
        }

        this.id = id.trim();
        this.name = name.trim();
        this.type = type;
        this.location = location.trim();
        this.seatingCapacity = seatingCapacity;
        this.active = active;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ResourceType getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public int getSeatingCapacity() {
        return seatingCapacity;
    }

    public boolean isActive() {
        return active;
    }

    void retire() {
        active = false;
    }

    @Override
    public String toString() {
        String seats = type.hasSeatingCapacity() ? "Seats: " + seatingCapacity : "";
        return String.format("%-5s | %-11s | %-34s | %-20s | %s",
                id, type, name, location, seats);
    }
}
