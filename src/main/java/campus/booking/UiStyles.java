package campus.booking;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

final class UiStyles {
    static final Color INK = new Color(37, 53, 66);
    static final Color MUTED = new Color(111, 128, 141);
    static final Color TEAL = new Color(0, 126, 122);
    static final Color BORDER = new Color(222, 230, 234);
    static final Color ERROR_COLOR = new Color(181, 77, 64);
    static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font SMALL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    static final Font LABEL_FONT = new Font("Segoe UI", Font.BOLD, 11);
    static final Font SMALL_BOLD_FONT = new Font("Segoe UI", Font.BOLD, 12);

    private UiStyles() { }

    static void styleInput(JTextField field) {
        field.setFont(BODY_FONT);
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setPreferredSize(new Dimension(100, 38));
    }

    static void styleButton(JButton button) {
        button.setFont(BODY_FONT);
        button.setRolloverEnabled(false);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.CENTER);
    }

    static void stylePrimaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(TEAL);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);
    }

    static void styleDateButton(JButton button) {
        button.setFont(BODY_FONT);
        button.setForeground(INK);
        button.setBackground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        button.setPreferredSize(new Dimension(100, 38));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);
    }

    static void styleCard(JPanel panel) {
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(18, 18, 18, 18)));
    }

    static JPanel sectionHeading(String titleText, String subtitleText) {
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(INK);
        JLabel subtitle = new JLabel(subtitleText);
        subtitle.setFont(SMALL_FONT);
        subtitle.setForeground(MUTED);
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.SOUTH);
        return heading;
    }

    static JScrollPane tableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(Color.WHITE);
        ScrollBarStyler.style(scroll);
        return scroll;
    }

    static void styleTable(JTable table) {
        table.setFont(SMALL_FONT);
        table.setForeground(INK);
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(237, 241, 243));
        table.setSelectionBackground(new Color(220, 240, 238));
        table.setSelectionForeground(INK);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(LABEL_FONT);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(new Color(248, 250, 251));
        table.getTableHeader().setPreferredSize(new Dimension(100, 34));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                    boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 9, 0, 9));
                if (!selected) component.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 252, 252));
                return component;
            }
        });
    }

    static void setColumnWidths(JTable table, int... widths) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int column = 0; column < widths.length && column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }
}
