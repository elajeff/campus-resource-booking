package campus.booking;

public enum ResourceType {
    BOOK(false),
    CLASSROOM(true),
    LAB(true),
    STUDY_ROOM(true),
    EQUIPMENT(false);

    private final boolean seatingCapacityRelevant;

    ResourceType(boolean seatingCapacityRelevant) {
        this.seatingCapacityRelevant = seatingCapacityRelevant;
    }

    public boolean hasSeatingCapacity() {
        return seatingCapacityRelevant;
    }
}
