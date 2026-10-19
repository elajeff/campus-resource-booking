package campus.booking;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Keeps scrollbars consistent in the main window and scrollable control popups. */
final class ScrollBarStyler {
    private ScrollBarStyler() { }

    static void style(JScrollPane scrollPane) {
        style(scrollPane.getVerticalScrollBar());
        style(scrollPane.getHorizontalScrollBar());
    }

    static void styleComboBoxPopup(JComboBox<?> comboBox) {
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                SwingUtilities.invokeLater(() -> {
                    Object popup = comboBox.getUI().getAccessibleChild(comboBox, 0);
                    if (popup instanceof Component component) {
                        styleNestedScrollPanes(component);
                    }
                });
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) { }
        });
    }

    private static void styleNestedScrollPanes(Component component) {
        if (component instanceof JScrollPane scrollPane) {
            style(scrollPane);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleNestedScrollPanes(child);
            }
        }
    }

    private static void style(JScrollBar scrollBar) {
        scrollBar.setUI(new CompactScrollBarUI());
        scrollBar.setPreferredSize(new Dimension(12, 12));
        scrollBar.setOpaque(false);
        scrollBar.setBorder(BorderFactory.createEmptyBorder());
    }

    private static final class CompactScrollBarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return emptyButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return emptyButton();
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            graphics.setColor(new Color(239, 243, 245));
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                int width = 4;
                int x = bounds.x + (bounds.width - width) / 2;
                graphics.fillRoundRect(x, bounds.y + 2, width, Math.max(0, bounds.height - 4), 4, 4);
            } else {
                int height = 4;
                int y = bounds.y + (bounds.height - height) / 2;
                graphics.fillRoundRect(bounds.x + 2, y, Math.max(0, bounds.width - 4), height, 4, 4);
            }
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            graphics.setColor(new Color(169, 185, 192));
            int inset = 2;
            int width = Math.max(1, bounds.width - inset * 2);
            int height = Math.max(1, bounds.height - inset * 2);
            graphics.fillRoundRect(bounds.x + inset, bounds.y + inset, width, height, 8, 8);
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(24, 24);
        }

        private static JButton emptyButton() {
            JButton button = new JButton();
            Dimension noSize = new Dimension(0, 0);
            button.setPreferredSize(noSize);
            button.setMinimumSize(noSize);
            button.setMaximumSize(noSize);
            button.setFocusable(false);
            return button;
        }
    }
}
