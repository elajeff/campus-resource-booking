package campus.booking;

import static campus.booking.UiStyles.*;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

/** The saved reservations with the refresh and cancel buttons. */
final class ReservationPanel extends JPanel {
    private final ReservationTableModel model;
    private final JTable table;
    private final JButton cancelButton = new JButton("Cancel reservation");

    ReservationPanel(BookingService bookingService, Runnable onRefresh, Runnable onCancel) {
        model = new ReservationTableModel(bookingService);
        table = new JTable(model);
        styleTable(table);
        setColumnWidths(table, 90, 220, 160, 145, 145);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                cancelButton.setEnabled(table.getSelectedRow() >= 0);
            }
        });

        styleCard(this);
        setLayout(new BorderLayout(0, 10));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(sectionHeading("Reservations", "Bookings saved on this device"), BorderLayout.WEST);
        styleButton(cancelButton);
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(event -> onCancel.run());
        JButton refreshButton = new JButton("Refresh");
        styleButton(refreshButton);
        refreshButton.setToolTipText("Reload the catalog and bookings from the database");
        refreshButton.addActionListener(event -> onRefresh.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(cancelButton);
        heading.add(actions, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);
        JScrollPane scrollPane = tableScroll(table);
        scrollPane.setPreferredSize(new Dimension(100, 150));
        add(scrollPane, BorderLayout.CENTER);
        setMinimumSize(new Dimension(0, 110));
    }

    void setReservations(List<Reservation> reservations) {
        model.setReservations(reservations);
        cancelButton.setEnabled(false);
    }

    /** Returns the reference of the selected row, or null when nothing is selected. */
    String selectedReservationId() {
        int selectedRow = table.getSelectedRow();
        return selectedRow < 0 ? null : (String) model.getValueAt(table.convertRowIndexToModel(selectedRow), 0);
    }
}
