package campus.booking;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/** Applies the application's light, neutral style to choice controls. */
final class ComboBoxStyler {
    private static final Color INK = new Color(37, 53, 66);

    private ComboBoxStyler() { }

    static void style(JComboBox<?> comboBox, Font font) {
        comboBox.setFont(font);
        comboBox.setForeground(INK);
        comboBox.setBackground(Color.WHITE);
        comboBox.setOpaque(true);
        comboBox.setMaximumRowCount(8);
        comboBox.setRenderer(new WhiteCellRenderer());
        ScrollBarStyler.styleComboBoxPopup(comboBox);
    }

    static class WhiteCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            label.setForeground(INK);
            label.setBackground(Color.WHITE);
            label.setOpaque(true);
            return label;
        }
    }
}
