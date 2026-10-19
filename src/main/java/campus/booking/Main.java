package campus.booking;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setLookAndFeel();
            new SwingWorker<BookingService, Void>() {
                @Override
                protected BookingService doInBackground() {
                    Path databasePath = Path.of(System.getProperty("user.home"),
                            ".resource-booking", "bookings.db");
                    SQLiteBookingRepository repository = new SQLiteBookingRepository(databasePath);
                    repository.initialize(SampleData.createResources());
                    return new BookingService(repository.loadResources(), repository.loadReservations(), repository);
                }

                @Override
                protected void done() {
                    try {
                        BookingService service = get();
                        BookingFrame frame = new BookingFrame(service);
                        AppIcon.applyTo(frame);
                        frame.setVisible(true);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        showStartupError(exception);
                    } catch (ExecutionException exception) {
                        showStartupError(exception.getCause() == null ? exception : exception.getCause());
                    } catch (RuntimeException exception) {
                        showStartupError(exception);
                    }
                }
            }.execute();
        });
    }

    private static void showStartupError(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = "The database operation failed.";
        JOptionPane.showMessageDialog(null,
                "The application could not open its local database.\n\n" + message,
                "Could not start", JOptionPane.ERROR_MESSAGE);
    }

    private static void setLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    UIManager.put("control", new Color(244, 247, 249));
                    // Keep Nimbus-painted controls light; a dark base bleeds into combo and spinner controls.
                    UIManager.put("nimbusBase", new Color(244, 247, 249));
                    UIManager.put("nimbusBlueGrey", new Color(220, 230, 234));
                    UIManager.put("nimbusFocus", new Color(0, 126, 122));
                    UIManager.put("nimbusSelectionBackground", new Color(220, 240, 238));
                    UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing's default look and feel remains available if Nimbus cannot load.
        }
    }
}
