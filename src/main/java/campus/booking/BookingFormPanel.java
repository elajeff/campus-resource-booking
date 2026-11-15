package campus.booking;

import static campus.booking.UiStyles.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/** The "New reservation" card: selected resource, borrower, time slot and the feedback line. */
final class BookingFormPanel extends JPanel {
    private static final DateTimeFormatter PICKER_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM uuuu  ·  HH:mm", Locale.getDefault());
    private static final String NO_RESOURCE = "Choose a resource from the list";

    private final JTextArea selectedResourceField = new JTextArea(NO_RESOURCE);
    private final JTextField borrowerField = new JTextField();
    private final JButton startButton = new JButton();
    private final JButton endButton = new JButton();
    private final JTextArea feedbackField = new JTextArea(" ");
    private final Runnable onTimeSlotChanged;
    private LocalDateTime start = LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0);
    private LocalDateTime end = start.plusHours(1);

    BookingFormPanel(Runnable onCheckAvailability, Runnable onReserve, Runnable onTimeSlotChanged) {
        this.onTimeSlotChanged = onTimeSlotChanged;
        styleCard(this);
        setMinimumSize(new Dimension(310, 0));
        setLayout(new BorderLayout(0, 14));
        add(sectionHeading("New reservation", "Enter the person and time slot"), BorderLayout.NORTH);
        JScrollPane formScroll = new JScrollPane(createForm(onCheckAvailability));
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll.getViewport().setBackground(Color.WHITE);
        ScrollBarStyler.style(formScroll);
        add(formScroll, BorderLayout.CENTER);
        add(createActions(onReserve), BorderLayout.SOUTH);
    }

    private JPanel createForm(Runnable onCheckAvailability) {
        JPanel form = new ScrollableFormPanel();
        form.setLayout(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 6, 0);

        addFormLabel(form, constraints, 0, "Selected resource");
        selectedResourceField.setEditable(false);
        selectedResourceField.setFocusable(false);
        selectedResourceField.setLineWrap(true);
        selectedResourceField.setWrapStyleWord(true);
        selectedResourceField.setFont(new Font("Segoe UI", Font.BOLD, 13));
        selectedResourceField.setForeground(INK);
        selectedResourceField.setBackground(Color.WHITE);
        selectedResourceField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        addFormComponent(form, constraints, 1, selectedResourceField);

        addFormLabel(form, constraints, 2, "Borrower name");
        styleInput(borrowerField);
        addFormComponent(form, constraints, 3, borrowerField);

        addFormLabel(form, constraints, 4, "Start date and time");
        styleDateButton(startButton);
        startButton.setText(start.format(PICKER_FORMAT));
        startButton.setToolTipText("Click to choose a date and time");
        startButton.addActionListener(event -> chooseDateTime(true));
        addFormComponent(form, constraints, 5, startButton);
        addFormLabel(form, constraints, 6, "End date and time");
        styleDateButton(endButton);
        endButton.setText(end.format(PICKER_FORMAT));
        endButton.setToolTipText("Click to choose a date and time");
        endButton.addActionListener(event -> chooseDateTime(false));
        addFormComponent(form, constraints, 7, endButton);

        JButton availabilityButton = new JButton("Check availability");
        styleButton(availabilityButton);
        availabilityButton.addActionListener(event -> onCheckAvailability.run());
        constraints.gridy = 8;
        constraints.insets = new Insets(8, 0, 0, 0);
        form.add(availabilityButton, constraints);

        constraints.gridy = 9;
        constraints.weighty = 1;
        form.add(new JLabel(" "), constraints);
        return form;
    }

    private JPanel createActions(Runnable onReserve) {
        JPanel actions = new JPanel(new BorderLayout(0, 6));
        actions.setOpaque(false);
        actions.setMinimumSize(new Dimension(100, 94));

        feedbackField.setEditable(false);
        feedbackField.setFocusable(false);
        feedbackField.setLineWrap(true);
        feedbackField.setWrapStyleWord(true);
        feedbackField.setRows(2);
        feedbackField.setPreferredSize(new Dimension(200, 40));
        feedbackField.setFont(SMALL_FONT);
        feedbackField.setForeground(MUTED);
        feedbackField.setBackground(Color.WHITE);
        feedbackField.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        actions.add(feedbackField, BorderLayout.CENTER);

        JButton reserveButton = new JButton("Reserve resource");
        stylePrimaryButton(reserveButton);
        reserveButton.setPreferredSize(new Dimension(100, 48));
        reserveButton.setMinimumSize(new Dimension(0, 48));
        reserveButton.addActionListener(event -> onReserve.run());
        actions.add(reserveButton, BorderLayout.SOUTH);
        return actions;
    }

    LocalDateTime start() {
        return start;
    }

    LocalDateTime end() {
        return end;
    }

    String borrowerName() {
        return borrowerField.getText();
    }

    /** Clears the borrower field unless it was edited after the booking was sent. */
    void clearBorrower(String bookedName) {
        if (borrowerField.getText().equals(bookedName)) borrowerField.setText("");
    }

    void showResource(Resource resource) {
        selectedResourceField.setText(resource == null ? NO_RESOURCE : resource.getId() + "  ·  " + resource.getName());
    }

    void showFeedback(String message, Color color) {
        feedbackField.setText(message);
        feedbackField.setForeground(color);
    }

    private void chooseDateTime(boolean isStart) {
        LocalDateTime current = isStart ? start : end;
        String title = isStart ? "Choose start date and time" : "Choose end date and time";
        DateTimePickerDialog.choose(SwingUtilities.getWindowAncestor(this), title, current).ifPresent(value -> {
            if (isStart && !value.isAfter(LocalDateTime.now())) {
                showFeedback("Reservation start time must be in the future.", ERROR_COLOR);
                return;
            }
            if (isStart && !end.isAfter(value)) {
                showFeedback("Start time must be before the end time.", ERROR_COLOR);
                return;
            }
            if (!isStart && !value.isAfter(start)) {
                showFeedback("End time must be after the start time.", ERROR_COLOR);
                return;
            }
            if (isStart) {
                start = value;
                startButton.setText(value.format(PICKER_FORMAT));
            } else {
                end = value;
                endButton.setText(value.format(PICKER_FORMAT));
            }
            showFeedback(" ", MUTED);
            onTimeSlotChanged.run();
        });
    }

    private static void addFormLabel(JPanel panel, GridBagConstraints constraints, int row, String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT);
        label.setForeground(MUTED);
        constraints.gridy = row;
        constraints.insets = new Insets(row == 0 ? 0 : 5, 0, 2, 0);
        panel.add(label, constraints);
    }

    private static void addFormComponent(JPanel panel, GridBagConstraints constraints, int row, Component component) {
        constraints.gridy = row;
        constraints.insets = new Insets(0, 0, 6, 0);
        panel.add(component, constraints);
    }

    private static final class ScrollableFormPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(20, visibleRect.height - 20);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
