package campus.booking;

import java.util.List;
import javax.swing.table.AbstractTableModel;

final class ResourceTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"ID", "Type", "Resource", "Location", "Seats"};

    private List<Resource> resources;

    ResourceTableModel(List<Resource> resources) {
        this.resources = List.copyOf(resources);
    }

    void setResources(List<Resource> resources) {
        this.resources = List.copyOf(resources);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return resources.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Resource resource = resources.get(row);
        return switch (column) {
            case 0 -> resource.getId();
            case 1 -> resource.getType().toString().replace('_', ' ');
            case 2 -> resource.getName();
            case 3 -> resource.getLocation();
            case 4 -> resource.getType().hasSeatingCapacity()
                    ? resource.getSeatingCapacity()
                    : "—";
            default -> "";
        };
    }

    Resource getResource(int row) {
        return resources.get(row);
    }
}
