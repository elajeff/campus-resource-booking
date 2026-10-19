package campus.booking;

import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setLookAndFeel();
            try {
                Path databasePath = Path.of(System.getProperty("user.home"), ".resource-booking", "bookings.db");
                SQLiteBookingRepository repository = new SQLiteBookingRepository(databasePath);
                repository.initialize(SampleData.createResources());
                BookingService service = new BookingService(
                        repository.loadResources(), repository.loadReservations(), repository);
                BookingFrame frame = new BookingFrame(service);
                AppIcon.applyTo(frame);
                frame.setVisible(true);
            } catch (RuntimeException exception) {
                javax.swing.JOptionPane.showMessageDialog(null,
                        "The application could not open its local database.\n\n" + exception.getMessage(),
                        "Could not start", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
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
