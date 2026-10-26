package campus.booking;

import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.table.AbstractTableModel;

final class ReservationTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Reference", "Resource", "Borrower", "Start", "End"};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final BookingService bookingService;
    private List<Reservation> reservations = List.of();

    ReservationTableModel(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    void setReservations(List<Reservation> reservations) {
        this.reservations = List.copyOf(reservations);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return reservations.size();
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
        Reservation reservation = reservations.get(row);
        return switch (column) {
            case 0 -> reservation.getId();
            case 1 -> bookingService.findResource(reservation.getResourceId())
                    .map(Resource::getName).orElse("Unknown resource");
            case 2 -> reservation.getBorrowerName();
            case 3 -> reservation.getStart().format(DATE_FORMAT);
            case 4 -> reservation.getEnd().format(DATE_FORMAT);
            default -> "";
        };
    }
}
