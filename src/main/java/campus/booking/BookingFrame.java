package campus.booking;

import static campus.booking.UiStyles.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class BookingFrame extends JFrame {
    private static final Color BACKGROUND = new Color(244, 247, 249);
    private static final Color NAVY = new Color(25, 46, 62);

    private final BookingService bookingService;
    private final BookingFormPanel bookingForm;
    private final ResourcePanel resourcePanel;
    private final ReservationPanel reservationPanel;
    private final JPanel busyGlassPane = new JPanel(new GridBagLayout());
    private final JLabel busyMessage = new JLabel();
    private boolean databaseTaskRunning;

    public BookingFrame(BookingService bookingService) {
        super("Campus Resource Booking");
        this.bookingService = bookingService;
        bookingForm = new BookingFormPanel(this::checkAvailability, this::createReservation, this::updateTimeSlot);
        resourcePanel = new ResourcePanel(bookingService, this::showAddResourceDialog, this::retireSelectedResource,
                resource -> {
                    bookingForm.showResource(resource);
                    if (resource != null) bookingForm.showFeedback(" ", MUTED);
                });
        updateTimeSlot();
        reservationPanel = new ReservationPanel(bookingService, this::reloadData, this::cancelReservation);

        configureWindow();
        configureBusyIndicator();
        setLayout(new BorderLayout(0, 18));
        setBackground(BACKGROUND);
        add(createHeader(), BorderLayout.NORTH);

        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resourcePanel, bookingForm);
        contentSplit.setResizeWeight(0.68);
        contentSplit.setContinuousLayout(true);
        contentSplit.setOneTouchExpandable(true);
        contentSplit.setBorder(BorderFactory.createEmptyBorder());
        contentSplit.setDividerSize(8);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));
        content.add(contentSplit, BorderLayout.CENTER);
        content.setMinimumSize(new Dimension(0, 340));

        JSplitPane workspaceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, content, reservationPanel);
        workspaceSplit.setResizeWeight(0.72);
        workspaceSplit.setContinuousLayout(true);
        workspaceSplit.setOneTouchExpandable(true);
        workspaceSplit.setBorder(BorderFactory.createEmptyBorder());
        workspaceSplit.setDividerSize(8);
        add(workspaceSplit, BorderLayout.CENTER);
        setResizable(true);
        SwingUtilities.invokeLater(() -> {
            contentSplit.setDividerLocation(0.68);
            workspaceSplit.setDividerLocation(0.72);
        });
        loadReservations();
    }

    private void configureBusyIndicator() {
        busyGlassPane.setOpaque(false);
        busyGlassPane.setFocusable(true);
        busyGlassPane.setFocusTraversalKeysEnabled(false);
        busyMessage.setFont(new Font("Segoe UI", Font.BOLD, 13));
        busyMessage.setForeground(INK);
        busyMessage.setOpaque(true);
        busyMessage.setBackground(Color.WHITE);
        busyMessage.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        busyGlassPane.add(busyMessage);
        busyGlassPane.addMouseListener(new java.awt.event.MouseAdapter() { });
        setGlassPane(busyGlassPane);
        busyGlassPane.setVisible(false);
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 620));
        setSize(1180, 780);
        setLocationRelativeTo(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));

        JPanel titles = new JPanel(new BorderLayout(0, 5));
        titles.setOpaque(false);
        JLabel title = new JLabel("Campus Resource Booking");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JLabel subtitle = new JLabel("Find a space or item, check its time slot, and make a reservation.");
        subtitle.setForeground(new Color(197, 212, 222));
        subtitle.setFont(BODY_FONT);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);

        Image appIcon = AppIcon.imageAtSize(48);
        if (appIcon != null) {
            JLabel logo = new JLabel(new ImageIcon(appIcon));
            logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
            header.add(logo, BorderLayout.WEST);
        }

        JLabel session = new JLabel("  SAVED LOCALLY  ");
        session.setForeground(new Color(220, 239, 237));
        session.setFont(LABEL_FONT);
        session.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(72, 112, 125)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        header.add(titles, BorderLayout.CENTER);
        header.add(session, BorderLayout.EAST);
        return header;
    }

    private void showAddResourceDialog() {
        new AddResourceDialog(this, bookingService, () -> {
            resourcePanel.reload();
            loadReservations();
            bookingForm.showFeedback("Resource added to the catalog.", TEAL);
        }).setVisible(true);
    }

    private void retireSelectedResource() {
        Resource resource = resourcePanel.selectedResource();
        if (resource == null) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Retire " + resource.getId() + " · " + resource.getName() + " from the active catalog?",
                "Retire resource", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        runDatabaseTask("Retiring resource…", () -> {
            bookingService.retireResource(resource.getId());
            return null;
        }, ignored -> {
            resourcePanel.reload();
            bookingForm.showFeedback("Resource retired from the active catalog.", TEAL);
        }, exception -> bookingForm.showFeedback(messageOf(exception), ERROR_COLOR));
    }

    private void reloadData() {
        runDatabaseTask("Refreshing catalog and bookings…", () -> {
            bookingService.refresh();
            return null;
        }, ignored -> {
            resourcePanel.reload();
            loadReservations();
            bookingForm.showFeedback("Catalog and bookings refreshed.", TEAL);
        }, exception -> bookingForm.showFeedback(messageOf(exception), ERROR_COLOR));
    }

    private void checkAvailability() {
        Resource resource = resourcePanel.selectedResource();
        if (resource == null) {
            bookingForm.showFeedback("Select a resource from the list first.", ERROR_COLOR);
            return;
        }
        LocalDateTime start = bookingForm.start();
        LocalDateTime end = bookingForm.end();
        runDatabaseTask("Checking availability…", () -> {
            bookingService.refresh();
            return bookingService.isAvailable(resource.getId(), start, end);
        }, available -> {
            resourcePanel.reload();
            loadReservations();
            bookingForm.showFeedback(available ? "Available for this time slot." : "Already reserved during this time slot.",
                    available ? TEAL : ERROR_COLOR);
        }, exception -> bookingForm.showFeedback(messageOf(exception), ERROR_COLOR));
    }

    private void createReservation() {
        Resource resource = resourcePanel.selectedResource();
        if (resource == null) {
            bookingForm.showFeedback("Select a resource from the list first.", ERROR_COLOR);
            return;
        }
        String borrowerName = bookingForm.borrowerName();
        LocalDateTime start = bookingForm.start();
        LocalDateTime end = bookingForm.end();
        runDatabaseTask("Saving reservation…", () ->
                bookingService.reserve(resource.getId(), borrowerName, start, end), reservation -> {
            bookingForm.clearBorrower(borrowerName);
            resourcePanel.reload();
            loadReservations();
            bookingForm.showFeedback("Reservation created · " + reservation.getId(), TEAL);
        }, exception -> bookingForm.showFeedback(messageOf(exception), ERROR_COLOR));
    }

    private void cancelReservation() {
        String reservationId = reservationPanel.selectedReservationId();
        if (reservationId == null) return;
        runDatabaseTask("Cancelling reservation…", () -> bookingService.cancel(reservationId), cancelled -> {
            resourcePanel.reload();
            loadReservations();
            if (cancelled) {
                bookingForm.showFeedback("Reservation " + reservationId + " cancelled.", TEAL);
            } else {
                bookingForm.showFeedback("That reservation is no longer active.", ERROR_COLOR);
            }
        }, exception -> bookingForm.showFeedback(messageOf(exception), ERROR_COLOR));
    }

    private void updateTimeSlot() {
        resourcePanel.setTimeSlot(bookingForm.start(), bookingForm.end());
    }

    private void loadReservations() {
        reservationPanel.setReservations(bookingService.getReservations());
    }

    private <T> void runDatabaseTask(String message, Callable<T> task,
                                     Consumer<? super T> onSuccess,
                                     Consumer<? super Throwable> onFailure) {
        if (databaseTaskRunning) return;
        databaseTaskRunning = true;
        busyMessage.setText(message);
        busyGlassPane.setVisible(true);
        busyGlassPane.requestFocusInWindow();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                databaseTaskRunning = false;
                busyGlassPane.setVisible(false);
                setCursor(Cursor.getDefaultCursor());
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    onFailure.accept(exception);
                } catch (ExecutionException exception) {
                    onFailure.accept(exception.getCause() == null ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private static String messageOf(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "The database operation failed." : message;
    }
}
