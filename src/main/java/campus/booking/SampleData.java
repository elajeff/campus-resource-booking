package campus.booking;

import java.util.ArrayList;
import java.util.List;

/** Provides a larger, generic example catalog for running the application. */
public final class SampleData {
    private SampleData() {
    }

    public static List<Resource> createResources() {
        List<Resource> resources = new ArrayList<>();

        int nextBookId = 1;
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId, "Clean Code", "Library", 3);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Introduction to Algorithms", "Library", 3);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId, "Effective Java", "Library", 3);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "The Pragmatic Programmer", "Library", 3);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId, "Design Patterns", "Library", 2);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Database System Concepts", "Library", 2);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Operating System Concepts", "Library", 2);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Computer Networking", "Library", 2);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId, "Refactoring", "Library", 1);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Head First Design Patterns", "Library", 1);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "The Mythical Man-Month", "Library", 1);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId, "Code Complete", "Library", 1);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Domain-Driven Design", "Library", 1);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Structure and Interpretation of Computer Programs", "Library", 1);
        nextBookId = addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Artificial Intelligence: A Modern Approach", "Library", 1);
        addCopies(resources, ResourceType.BOOK, "B", nextBookId,
                "Computer Organization and Design", "Library", 1);

        resources.add(new Resource("C101", "Classroom 101", ResourceType.CLASSROOM, "Building A", 30));
        resources.add(new Resource("C102", "Classroom 102", ResourceType.CLASSROOM, "Building A", 24));
        resources.add(new Resource("C103", "Classroom 103", ResourceType.CLASSROOM, "Building A", 36));
        resources.add(new Resource("C204", "Lecture Room 204", ResourceType.CLASSROOM, "Building B", 80));
        resources.add(new Resource("C205", "Seminar Room 205", ResourceType.CLASSROOM, "Building B", 16));
        resources.add(new Resource("C206", "Seminar Room 206", ResourceType.CLASSROOM, "Building B", 20));
        resources.add(new Resource("C301", "Lecture Room 301", ResourceType.CLASSROOM, "Building C", 60));
        resources.add(new Resource("C302", "Lecture Room 302", ResourceType.CLASSROOM, "Building C", 48));

        resources.add(new Resource("L001", "Computer Lab A", ResourceType.LAB, "Technology Building", 24));
        resources.add(new Resource("L002", "Computer Lab B", ResourceType.LAB, "Technology Building", 20));
        resources.add(new Resource("L003", "Computer Lab C", ResourceType.LAB, "Technology Building", 28));
        resources.add(new Resource("L004", "Electronics Lab", ResourceType.LAB, "Engineering Building", 16));
        resources.add(new Resource("L005", "Robotics Lab", ResourceType.LAB, "Engineering Building", 18));
        resources.add(new Resource("L006", "Media Production Lab", ResourceType.LAB, "Arts Building", 14));

        resources.add(new Resource("S001", "Study Room 1", ResourceType.STUDY_ROOM, "Library", 6));
        resources.add(new Resource("S002", "Group Study Room 2", ResourceType.STUDY_ROOM, "Library", 8));
        resources.add(new Resource("S003", "Quiet Study Room 3", ResourceType.STUDY_ROOM, "Library", 4));
        resources.add(new Resource("S004", "Study Room 4", ResourceType.STUDY_ROOM, "Library", 6));
        resources.add(new Resource("S005", "Study Room 5", ResourceType.STUDY_ROOM, "Library", 4));
        resources.add(new Resource("S006", "Project Room 6", ResourceType.STUDY_ROOM, "Building B", 10));
        resources.add(new Resource("S007", "Project Room 7", ResourceType.STUDY_ROOM, "Building C", 8));
        resources.add(new Resource("S008", "Quiet Study Room 8", ResourceType.STUDY_ROOM, "Building C", 4));

        int nextEquipmentId = 1;
        nextEquipmentId = addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId,
                "Laptop", "Equipment Desk", 5);
        nextEquipmentId = addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId,
                "Portable Projector", "Equipment Desk", 3);
        nextEquipmentId = addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId,
                "Camera and Tripod Kit", "Media Desk", 2);
        nextEquipmentId = addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId,
                "USB Microphone Kit", "Media Desk", 2);
        nextEquipmentId = addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId,
                "Arduino Starter Kit", "Engineering Desk", 4);
        nextEquipmentId = addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId,
                "Scientific Calculator", "Library Desk", 2);
        addCopies(resources, ResourceType.EQUIPMENT, "E", nextEquipmentId, "Portable Speaker", "Media Desk", 2);

        return resources;
    }

    private static int addCopies(List<Resource> resources, ResourceType type, String idPrefix,
                                 int firstId, String name, String location, int copies) {
        for (int copy = 1; copy <= copies; copy++) {
            String id = idPrefix + String.format("%03d", firstId + copy - 1);
            String copyName = copies == 1 ? name : name + " · Copy " + copy;
            resources.add(new Resource(id, copyName, type, location, 0));
        }
        return firstId + copies;
    }
}
