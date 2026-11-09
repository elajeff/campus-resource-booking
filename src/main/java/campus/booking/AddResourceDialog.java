package campus.booking;

import static campus.booking.UiStyles.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

final class AddResourceDialog extends JDialog {
    AddResourceDialog(Window owner, BookingService bookingService, Runnable onAdded) {
        super(owner, "Add a resource", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField idField = new JTextField(18);
        JTextField nameField = new JTextField(18);
        JComboBox<ResourceType> typeField = new JComboBox<>(ResourceType.values());
        JTextField locationField = new JTextField(18);
        JTextField seatsField = new JTextField("0", 18);
        for (JTextField field : new JTextField[]{idField, nameField, locationField, seatsField}) {
            styleInput(field);
        }
        seatsField.setEnabled(false);
        ComboBoxStyler.style(typeField, new Font("Segoe UI", Font.PLAIN, 13));
        typeField.addActionListener(event -> {
            boolean hasSeats = ((ResourceType) typeField.getSelectedItem()).hasSeatingCapacity();
            seatsField.setEnabled(hasSeats);
            if (hasSeats && (seatsField.getText().isBlank() || "0".equals(seatsField.getText().trim()))) {
                seatsField.setText("24");
            } else if (!hasSeats) {
                seatsField.setText("0");
            }
        });

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(Color.WHITE);
        fields.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(5, 0, 5, 10);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0;
        addDialogField(fields, constraints, 0, "Resource ID", idField);
        addDialogField(fields, constraints, 1, "Name", nameField);
        addDialogField(fields, constraints, 2, "Type", typeField);
        addDialogField(fields, constraints, 3, "Location", locationField);
        addDialogField(fields, constraints, 4, "Seat capacity", seatsField);

        JButton addButton = new JButton("Add resource");
        stylePrimaryButton(addButton);
        JButton closeButton = new JButton("Cancel");
        styleButton(closeButton);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        actions.setBackground(Color.WHITE);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        actions.add(closeButton);
        actions.add(addButton);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(Color.WHITE);
        content.add(fields, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);

        closeButton.addActionListener(event -> dispose());
        addButton.addActionListener(event -> {
            Resource resource;
            try {
                ResourceType type = (ResourceType) typeField.getSelectedItem();
                int seats = type.hasSeatingCapacity() ? Integer.parseInt(seatsField.getText().trim()) : 0;
                resource = new Resource(idField.getText(), nameField.getText(), type,
                        locationField.getText(), seats);
            } catch (NumberFormatException exception) {
                showError("Seat capacity must be a whole number.");
                return;
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
                return;
            }

            Component[] dialogControls = {idField, nameField, typeField, locationField,
                    seatsField, addButton, closeButton};
            for (Component control : dialogControls) control.setEnabled(false);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    bookingService.addResource(resource);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        onAdded.run();
                        dispose();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        showError("Saving was interrupted.");
                    } catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        showError(cause == null ? exception.getMessage() : cause.getMessage());
                    } finally {
                        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                        for (Component control : dialogControls) control.setEnabled(true);
                        seatsField.setEnabled(((ResourceType) typeField.getSelectedItem()).hasSeatingCapacity());
                    }
                }
            }.execute();
        });
        getRootPane().setDefaultButton(addButton);
        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static void addDialogField(JPanel panel, GridBagConstraints constraints,
                                       int row, String label, JComponent field) {
        JLabel caption = new JLabel(label);
        caption.setFont(new Font("Segoe UI", Font.BOLD, 12));
        caption.setForeground(INK);
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(caption, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(5, 0, 5, 0);
        panel.add(field, constraints);
        constraints.insets = new Insets(5, 0, 5, 10);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Could not add resource", JOptionPane.ERROR_MESSAGE);
    }
}
