package campus.booking;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Loads the application artwork at the sizes used by window and taskbar icons. */
final class AppIcon {
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final List<Image> ICONS = loadIconImages();

    private AppIcon() {
    }

    static void applyTo(Window window) {
        if (ICONS.isEmpty()) {
            return;
        }
        window.setIconImages(ICONS);
        if (window instanceof Frame) {
            setTaskbarIcon(ICONS.get(ICONS.size() - 1));
        }
    }

    static Image imageAtSize(int size) {
        for (int index = 0; index < ICON_SIZES.length; index++) {
            if (ICON_SIZES[index] == size) {
                return ICONS.get(index);
            }
        }
        return null;
    }

    private static List<Image> loadIconImages() {
        URL resource = AppIcon.class.getResource("/icons/app-icon.png");
        if (resource == null) {
            System.err.println("Application icon resource was not found.");
            return List.of();
        }

        try {
            BufferedImage source = ImageIO.read(resource);
            if (source == null) {
                System.err.println("Application icon resource is not a supported image.");
                return List.of();
            }
            return createIconSizes(source);
        } catch (IOException exception) {
            System.err.println("Unable to load the application icon: " + exception.getMessage());
            return List.of();
        }
    }

    private static List<Image> createIconSizes(BufferedImage source) {
        List<Image> icons = new ArrayList<>(ICON_SIZES.length);
        for (int size : ICON_SIZES) {
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, size, size, null);
            graphics.dispose();
            icons.add(scaled);
        }
        return List.copyOf(icons);
    }

    private static void setTaskbarIcon(Image icon) {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(icon);
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // The frame icon still applies in the title bar and Windows taskbar.
        }
    }
}
