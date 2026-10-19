package campus.booking;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicSpinnerUI;

/** A small click-based calendar and time chooser used by the booking form. */
final class DateTimePickerDialog extends JDialog {
    private static final Color INK = new Color(37, 53, 66);
    private static final Color MUTED = new Color(111, 128, 141);
    private static final Color TEAL = new Color(0, 126, 122);
    private static final Color BORDER = new Color(222, 230, 234);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font DAY_FONT = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font WEEKDAY_FONT = new Font("Segoe UI", Font.BOLD, 11);

    private final JComboBox<Month> monthBox = new JComboBox<>(Month.values());
    private final JSpinner yearSpinner;
    private final JComboBox<String> hourBox = new JComboBox<>();
    private final JComboBox<String> minuteBox = new JComboBox<>();
    private final JPanel calendar = new JPanel(new GridLayout(0, 7, 3, 3));
    private final JButton chooseButton = new JButton("Use selected time");
    private LocalDate selectedDate;
    private LocalDateTime result;

    static Optional<LocalDateTime> choose(Window owner, String title, LocalDateTime initial) {
        DateTimePickerDialog dialog = new DateTimePickerDialog(owner, title, initial);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private DateTimePickerDialog(Window owner, String title, LocalDateTime initial) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        LocalDateTime value = initial == null ? LocalDateTime.now().withSecond(0).withNano(0) : initial;
        selectedDate = value.toLocalDate();
        yearSpinner = new JSpinner(new SpinnerNumberModel(value.getYear(), 1900, 2200, 1));

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        AppIcon.applyTo(this);
        setContentPane(createContent());
        monthBox.setSelectedItem(value.getMonth());
        hourBox.setSelectedItem(String.format("%02d", value.getHour()));
        minuteBox.setSelectedItem(String.format("%02d", value.getMinute()));
        monthBox.addActionListener(event -> {
            selectedDate = null;
            updateCalendar();
        });
        yearSpinner.addChangeListener(event -> {
            selectedDate = null;
            updateCalendar();
        });
        updateCalendar();
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(18, 18, 16, 18)));

        JPanel calendarPanel = new JPanel(new BorderLayout(0, 10));
        calendarPanel.setOpaque(false);
        JPanel monthRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        monthRow.setOpaque(false);
        ComboBoxStyler.style(monthBox, new Font("Segoe UI", Font.BOLD, 14));
        monthBox.setRenderer(new ComboBoxStyler.WhiteCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Month month) {
                    label.setText(month.getDisplayName(TextStyle.FULL, Locale.getDefault()));
                }
                return label;
            }
        });
        styleYearSpinner();
        yearSpinner.setPreferredSize(new Dimension(82, 32));
        monthRow.add(monthBox);
        monthRow.add(yearSpinner);

        JPanel dayGrid = new JPanel(new BorderLayout(0, 5));
        dayGrid.setOpaque(false);
        JPanel weekdays = new JPanel(new GridLayout(1, 7, 3, 0));
        weekdays.setOpaque(false);
        for (String day : new String[] {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
            JLabel label = new JLabel(day, SwingConstants.CENTER);
            label.setFont(WEEKDAY_FONT);
            label.setForeground(MUTED);
            weekdays.add(label);
        }
        calendar.setOpaque(false);
        dayGrid.add(weekdays, BorderLayout.NORTH);
        dayGrid.add(calendar, BorderLayout.CENTER);
        calendarPanel.add(monthRow, BorderLayout.NORTH);
        calendarPanel.add(dayGrid, BorderLayout.CENTER);

        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        timePanel.setOpaque(false);
        JLabel timeLabel = new JLabel("Time");
        timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        timeLabel.setForeground(MUTED);
        fillTimeOptions(hourBox, 24);
        fillTimeOptions(minuteBox, 60);
        ComboBoxStyler.style(hourBox, BODY_FONT);
        ComboBoxStyler.style(minuteBox, BODY_FONT);
        hourBox.setPreferredSize(new Dimension(68, 32));
        minuteBox.setPreferredSize(new Dimension(68, 32));
        timePanel.add(timeLabel);
        timePanel.add(hourBox);
        timePanel.add(new JLabel(":"));
        timePanel.add(minuteBox);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(calendarPanel, BorderLayout.CENTER);
        center.add(timePanel, BorderLayout.SOUTH);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setRolloverEnabled(false);
        cancelButton.addActionListener(event -> dispose());
        chooseButton.setForeground(Color.WHITE);
        chooseButton.setBackground(TEAL);
        chooseButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chooseButton.setOpaque(true);
        chooseButton.setContentAreaFilled(true);
        chooseButton.setBorderPainted(false);
        chooseButton.setRolloverEnabled(false);
        chooseButton.setPreferredSize(new Dimension(150, 36));
        chooseButton.addActionListener(event -> acceptSelection());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelButton);
        actions.add(chooseButton);

        content.add(center, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        return content;
    }

    private void updateCalendar() {
        calendar.removeAll();
        Month month = (Month) monthBox.getSelectedItem();
        int year = (Integer) yearSpinner.getValue();
        YearMonth yearMonth = YearMonth.of(year, month);
        int leadingDays = yearMonth.atDay(1).getDayOfWeek().getValue() - 1;
        int daysInMonth = yearMonth.lengthOfMonth();
        for (int cell = 0; cell < 42; cell++) {
            int day = cell - leadingDays + 1;
            if (day < 1 || day > daysInMonth) {
                calendar.add(new JLabel(""));
                continue;
            }
            LocalDate date = yearMonth.atDay(day);
            JButton dayButton = new JButton(Integer.toString(day));
            dayButton.setFont(DAY_FONT);
            dayButton.setPreferredSize(new Dimension(38, 32));
            dayButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
            dayButton.setOpaque(true);
            dayButton.setContentAreaFilled(true);
            dayButton.setRolloverEnabled(false);
            dayButton.setFocusPainted(false);
            if (date.equals(selectedDate)) {
                dayButton.setBackground(TEAL);
                dayButton.setForeground(Color.WHITE);
                dayButton.setFont(dayButton.getFont().deriveFont(Font.BOLD));
            } else {
                dayButton.setBackground(Color.WHITE);
                dayButton.setForeground(INK);
            }
            dayButton.setBorder(BorderFactory.createLineBorder(date.equals(LocalDate.now()) ? TEAL : BORDER));
            dayButton.addActionListener(event -> {
                selectedDate = date;
                updateCalendar();
            });
            calendar.add(dayButton);
        }
        chooseButton.setEnabled(selectedDate != null);
        calendar.revalidate();
        calendar.repaint();
    }

    private void acceptSelection() {
        if (selectedDate == null) {
            return;
        }
        int hour = Integer.parseInt((String) hourBox.getSelectedItem());
        int minute = Integer.parseInt((String) minuteBox.getSelectedItem());
        result = selectedDate.atTime(hour, minute);
        dispose();
    }

    private static void fillTimeOptions(JComboBox<String> comboBox, int count) {
        for (int value = 0; value < count; value++) {
            comboBox.addItem(String.format("%02d", value));
        }
    }

    private void styleYearSpinner() {
        // Nimbus paints this spinner's upper arrow as disabled even when the model can advance.
        yearSpinner.setUI(new BasicSpinnerUI());
        yearSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        yearSpinner.setBackground(Color.WHITE);
        yearSpinner.setOpaque(true);
        if (yearSpinner.getEditor() instanceof JSpinner.NumberEditor editor) {
            editor.getFormat().setGroupingUsed(false);
            JFormattedTextField yearField = editor.getTextField();
            yearField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            yearField.setForeground(INK);
            yearField.setBackground(Color.WHITE);
            yearField.setOpaque(true);
            yearField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(0, 6, 0, 6)));
            yearField.setValue(yearSpinner.getValue());
        }
        for (Component component : yearSpinner.getComponents()) {
            if (component instanceof AbstractButton button) {
                button.setBackground(Color.WHITE);
                button.setForeground(INK);
                button.setOpaque(true);
                button.setContentAreaFilled(true);
                button.setFocusPainted(false);
                button.setRolloverEnabled(false);
            }
        }
    }

}
