package campus.booking;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

public class BookingFrame extends JFrame {
    private static final Color BACKGROUND = new Color(244, 247, 249);
    private static final Color NAVY = new Color(25, 46, 62);
    private static final Color INK = new Color(37, 53, 66);
    private static final Color MUTED = new Color(111, 128, 141);
    private static final Color TEAL = new Color(0, 126, 122);
    private static final Color BORDER = new Color(222, 230, 234);
    private static final DateTimeFormatter PICKER_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM uuuu  ·  HH:mm", Locale.getDefault());

    private final BookingService bookingService;
    private final ResourceTableModel resourceModel;
    private final ReservationTableModel reservationModel;
    private final JTable resourceTable;
    private final JTable reservationTable;
    private final JTextField searchField = new JTextField();
    private final JTextField borrowerField = new JTextField();
    private final JCheckBox availableOnlyCheck = new JCheckBox("Available for selected time");
    private final JLabel resourceCountLabel = new JLabel();
    private final JButton retireResourceButton = new JButton("Retire selected");
    private final JButton startButton = new JButton();
    private final JButton endButton = new JButton();
    private final JTextArea selectedResourceField = new JTextArea("Choose a resource from the list");
    private final JTextArea feedbackField = new JTextArea(" ");
    private final JButton cancelButton = new JButton("Cancel reservation");
    private final TableRowSorter<ResourceTableModel> resourceSorter;
    private final JPanel busyGlassPane = new JPanel(new GridBagLayout());
    private final JLabel busyMessage = new JLabel();
    private JSplitPane contentSplit;
    private JSplitPane workspaceSplit;
    private boolean databaseTaskRunning;
    private String selectedResourceId;
    private LocalDateTime startDateTime = LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0);
    private LocalDateTime endDateTime = startDateTime.plusHours(1);

    public BookingFrame(BookingService bookingService) {
        super("Campus Resource Booking");
        this.bookingService = bookingService;
        this.resourceModel = new ResourceTableModel(bookingService.getResources());
        this.reservationModel = new ReservationTableModel(bookingService);
        this.resourceTable = new JTable(resourceModel);
        this.reservationTable = new JTable(reservationModel);
        this.resourceSorter = new TableRowSorter<>(resourceModel);

        configureWindow();
        configureBusyIndicator();
        configureTables();
        setLayout(new BorderLayout(0, 18));
        setBackground(BACKGROUND);
        add(createHeader(), BorderLayout.NORTH);
        workspaceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createMainContent(), createReservationsPanel());
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

    private void configureTables() {
        styleTable(resourceTable);
        setColumnWidths(resourceTable, 58, 105, 245, 155, 86);
        resourceTable.setRowSorter(resourceSorter);
        resourceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resourceTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int selectedRow = resourceTable.getSelectedRow();
                if (selectedRow >= 0) {
                    Resource resource = resourceModel.getResource(resourceTable.convertRowIndexToModel(selectedRow));
                    selectedResourceId = resource.getId();
                    selectedResourceField.setText(resource.getId() + "  ·  " + resource.getName());
                    retireResourceButton.setEnabled(true);
                    adjustSelectedResourceField();
                    showFeedback(" ", MUTED);
                } else {
                    clearSelectedResource();
                }
            }
        });
        styleTable(reservationTable);
        setColumnWidths(reservationTable, 90, 220, 160, 145, 145);
        reservationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservationTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                cancelButton.setEnabled(reservationTable.getSelectedRow() >= 0);
            }
        });
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
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
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
        session.setFont(new Font("Segoe UI", Font.BOLD, 11));
        session.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(72, 112, 125)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        header.add(titles, BorderLayout.CENTER);
        header.add(session, BorderLayout.EAST);
        return header;
    }

    private JPanel createMainContent() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));

        JPanel resources = cardPanel();
        resources.setLayout(new BorderLayout(0, 12));
        resources.add(createResourcesHeading(), BorderLayout.NORTH);
        JPanel resourceBody = new JPanel(new BorderLayout(0, 10));
        resourceBody.setOpaque(false);
        searchField.setPreferredSize(new Dimension(200, 36));
        styleInput(searchField);
        searchField.putClientProperty("JTextField.placeholderText", "Search name, ID, or location");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { filterResources(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { filterResources(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { filterResources(); }
        });
        availableOnlyCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        availableOnlyCheck.setForeground(INK);
        availableOnlyCheck.setOpaque(false);
        availableOnlyCheck.addActionListener(event -> filterResources());
        JPanel resourceFilters = new JPanel(new BorderLayout(10, 0));
        resourceFilters.setOpaque(false);
        resourceFilters.add(searchField, BorderLayout.CENTER);
        resourceFilters.add(availableOnlyCheck, BorderLayout.EAST);
        resourceBody.add(resourceFilters, BorderLayout.NORTH);
        resourceBody.add(tableScroll(resourceTable), BorderLayout.CENTER);
        resources.add(resourceBody, BorderLayout.CENTER);

        JPanel booking = cardPanel();
        booking.setMinimumSize(new Dimension(310, 0));
        booking.setLayout(new BorderLayout(0, 14));
        booking.add(sectionHeading("New reservation", "Enter the person and time slot"), BorderLayout.NORTH);
        JScrollPane formScroll = new JScrollPane(createBookingForm());
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll.getViewport().setBackground(Color.WHITE);
        ScrollBarStyler.style(formScroll);
        booking.add(formScroll, BorderLayout.CENTER);
        booking.add(createBookingActions(), BorderLayout.SOUTH);

        resources.setMinimumSize(new Dimension(360, 0));
        contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, resources, booking);
        contentSplit.setResizeWeight(0.68);
        contentSplit.setContinuousLayout(true);
        contentSplit.setOneTouchExpandable(true);
        contentSplit.setBorder(BorderFactory.createEmptyBorder());
        contentSplit.setDividerSize(8);
        content.add(contentSplit, BorderLayout.CENTER);
        content.setMinimumSize(new Dimension(0, 340));
        return content;
    }

    private JPanel createResourcesHeading() {
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JPanel labels = new JPanel(new BorderLayout(0, 3));
        labels.setOpaque(false);
        resourceCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        resourceCountLabel.setForeground(INK);
        resourceCountLabel.setText("Resources (" + bookingService.getResources().size() + ")");
        JLabel subtitle = new JLabel("Search the catalog or add and retire inventory.");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(MUTED);
        labels.add(resourceCountLabel, BorderLayout.NORTH);
        labels.add(subtitle, BorderLayout.SOUTH);
        heading.add(labels, BorderLayout.CENTER);

        JButton addResourceButton = new JButton("Add resource");
        stylePrimaryButton(addResourceButton);
        addResourceButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addResourceButton.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        addResourceButton.addActionListener(event -> showAddResourceDialog());
        styleButton(retireResourceButton);
        retireResourceButton.setEnabled(false);
        retireResourceButton.addActionListener(event -> retireSelectedResource());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(addResourceButton);
        actions.add(retireResourceButton);
        heading.add(actions, BorderLayout.EAST);
        return heading;
    }

    private JPanel createBookingForm() {
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
        selectedResourceField.setRows(1);
        selectedResourceField.setFont(new Font("Segoe UI", Font.BOLD, 13));
        selectedResourceField.setForeground(INK);
        selectedResourceField.setBackground(Color.WHITE);
        selectedResourceField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(7, 9, 7, 9)));
        selectedResourceField.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                adjustSelectedResourceField();
            }
        });
        adjustSelectedResourceField();
        addFormComponent(form, constraints, 1, selectedResourceField);

        addFormLabel(form, constraints, 2, "Borrower name");
        styleInput(borrowerField);
        addFormComponent(form, constraints, 3, borrowerField);

        addFormLabel(form, constraints, 4, "Start date and time");
        styleDateButton(startButton);
        updateDateButton(startButton, startDateTime);
        startButton.setToolTipText("Click to choose a date and time");
        startButton.addActionListener(event -> chooseDateTime(true));
        addFormComponent(form, constraints, 5, startButton);
        addFormLabel(form, constraints, 6, "End date and time");
        styleDateButton(endButton);
        updateDateButton(endButton, endDateTime);
        endButton.setToolTipText("Click to choose a date and time");
        endButton.addActionListener(event -> chooseDateTime(false));
        addFormComponent(form, constraints, 7, endButton);

        JButton availabilityButton = new JButton("Check availability");
        styleButton(availabilityButton);
        availabilityButton.addActionListener(event -> checkAvailability());
        constraints.gridy = 8;
        constraints.insets = new Insets(8, 0, 0, 0);
        form.add(availabilityButton, constraints);

        constraints.gridy = 9;
        constraints.weighty = 1;
        form.add(new JLabel(" "), constraints);
        return form;
    }

    private JPanel createBookingActions() {
        JPanel actions = new JPanel(new BorderLayout(0, 6));
        actions.setOpaque(false);
        actions.setMinimumSize(new Dimension(100, 94));

        feedbackField.setEditable(false);
        feedbackField.setFocusable(false);
        feedbackField.setLineWrap(true);
        feedbackField.setWrapStyleWord(true);
        feedbackField.setRows(2);
        feedbackField.setPreferredSize(new Dimension(200, 40));
        feedbackField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        feedbackField.setForeground(MUTED);
        feedbackField.setBackground(Color.WHITE);
        feedbackField.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        actions.add(feedbackField, BorderLayout.CENTER);

        JButton reserveButton = new JButton("Reserve resource");
        stylePrimaryButton(reserveButton);
        reserveButton.setPreferredSize(new Dimension(100, 48));
        reserveButton.setMinimumSize(new Dimension(0, 48));
        reserveButton.addActionListener(event -> createReservation());
        actions.add(reserveButton, BorderLayout.SOUTH);
        return actions;
    }

    private JPanel createReservationsPanel() {
        JPanel panel = cardPanel();
        panel.setLayout(new BorderLayout(0, 10));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(sectionHeading("Reservations", "Bookings saved on this device"), BorderLayout.WEST);
        styleButton(cancelButton);
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(event -> cancelReservation());
        heading.add(cancelButton, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);
        JScrollPane scrollPane = tableScroll(reservationTable);
        scrollPane.setPreferredSize(new Dimension(100, 150));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setMinimumSize(new Dimension(0, 110));
        return panel;
    }

    private void filterResources() {
        String query = searchField.getText().trim();
        Set<String> availableIds = null;
        if (availableOnlyCheck.isSelected()) {
            try {
                availableIds = new HashSet<>();
                for (Resource resource : bookingService.availableResources(startDateTime, endDateTime)) {
                    availableIds.add(resource.getId().toLowerCase(Locale.ROOT));
                }
            } catch (IllegalArgumentException exception) {
                availableIds = Set.of();
                showFeedback(exception.getMessage(), new Color(181, 77, 64));
            }
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        Set<String> matches = availableIds;
        resourceSorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends ResourceTableModel, ? extends Integer> entry) {
                Resource resource = resourceModel.getResource(entry.getIdentifier());
                boolean matchesSearch = normalizedQuery.isEmpty()
                        || resource.getId().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || resource.getType().toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || resource.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || resource.getLocation().toLowerCase(Locale.ROOT).contains(normalizedQuery);
                return matchesSearch && (matches == null
                        || matches.contains(resource.getId().toLowerCase(Locale.ROOT)));
            }
        });
        boolean filtered = availableOnlyCheck.isSelected() || !normalizedQuery.isEmpty();
        resourceCountLabel.setText("Resources (" + resourceTable.getRowCount()
                + (filtered ? " shown)" : ")"));
    }

    private void refreshResources() {
        String previousSelection = selectedResourceId;
        resourceModel.setResources(bookingService.getResources());
        filterResources();
        if (previousSelection != null) {
            for (int modelRow = 0; modelRow < resourceModel.getRowCount(); modelRow++) {
                if (resourceModel.getResource(modelRow).getId().equalsIgnoreCase(previousSelection)) {
                    int viewRow = resourceTable.convertRowIndexToView(modelRow);
                    if (viewRow >= 0) {
                        resourceTable.setRowSelectionInterval(viewRow, viewRow);
                        return;
                    }
                    break;
                }
            }
        }
        clearSelectedResource();
    }

    private void clearSelectedResource() {
        selectedResourceId = null;
        selectedResourceField.setText("Choose a resource from the list");
        retireResourceButton.setEnabled(false);
        adjustSelectedResourceField();
    }

    private void showAddResourceDialog() {
        JDialog dialog = new JDialog(this, "Add a resource", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField idField = new JTextField(18);
        JTextField nameField = new JTextField(18);
        JComboBox<ResourceType> typeField = new JComboBox<>(ResourceType.values());
        JTextField locationField = new JTextField(18);
        JTextField seatsField = new JTextField("24", 18);
        for (JTextField field : new JTextField[]{idField, nameField, locationField, seatsField}) {
            styleInput(field);
        }
        seatsField.setText("0");
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
        dialog.setContentPane(content);

        closeButton.addActionListener(event -> dialog.dispose());
        addButton.addActionListener(event -> {
            Resource resource;
            try {
                ResourceType type = (ResourceType) typeField.getSelectedItem();
                int seats = type.hasSeatingCapacity() ? Integer.parseInt(seatsField.getText().trim()) : 0;
                resource = new Resource(idField.getText(), nameField.getText(), type,
                        locationField.getText(), seats);
            } catch (NumberFormatException exception) {
                showDialogError(dialog, "Seat capacity must be a whole number.");
                return;
            } catch (IllegalArgumentException exception) {
                showDialogError(dialog, exception.getMessage());
                return;
            }

            Component[] dialogControls = {idField, nameField, typeField, locationField,
                    seatsField, addButton, closeButton};
            for (Component control : dialogControls) control.setEnabled(false);
            runDatabaseTask("Adding resource…", () -> {
                bookingService.addResource(resource);
                return null;
            }, ignored -> {
                refreshResources();
                showFeedback("Resource added to the catalog.", TEAL);
                dialog.dispose();
            }, exception -> {
                for (Component control : dialogControls) control.setEnabled(true);
                showDialogError(dialog, messageOf(exception));
            });
        });
        dialog.getRootPane().setDefaultButton(addButton);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(420, dialog.getHeight()));
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
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

    private static void showDialogError(JDialog dialog, String message) {
        JOptionPane.showMessageDialog(dialog, message, "Could not add resource", JOptionPane.ERROR_MESSAGE);
    }

    private void retireSelectedResource() {
        Resource resource = selectedResourceId == null ? null
                : bookingService.findResource(selectedResourceId).orElse(null);
        if (resource == null || !resource.isActive()) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Retire " + resource.getId() + " · " + resource.getName() + " from the active catalog?",
                "Retire resource", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        runDatabaseTask("Retiring resource…", () -> {
            bookingService.retireResource(resource.getId());
            return null;
        }, ignored -> {
            clearSelectedResource();
            resourceTable.clearSelection();
            refreshResources();
            retireResourceButton.setEnabled(false);
            showFeedback("Resource retired from the active catalog.", TEAL);
        }, exception -> showFeedback(messageOf(exception), new Color(181, 77, 64)));
    }

    private void adjustSelectedResourceField() {
        FontMetrics metrics = selectedResourceField.getFontMetrics(selectedResourceField.getFont());
        Insets insets = selectedResourceField.getInsets();
        int availableWidth = selectedResourceField.getWidth() - insets.left - insets.right;
        if (availableWidth < 80) availableWidth = 250;

        int rows = 1;
        int currentLineWidth = 0;
        String[] words = selectedResourceField.getText().split("\\s+");
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            int wordWidth = metrics.stringWidth(word);
            int spaceWidth = wordIndex == 0 ? 0 : metrics.stringWidth(" ");
            if (currentLineWidth > 0 && currentLineWidth + spaceWidth + wordWidth > availableWidth) {
                rows++;
                currentLineWidth = 0;
            } else if (currentLineWidth > 0) {
                currentLineWidth += spaceWidth;
            }
            for (int index = 0; index < word.length(); index++) {
                int characterWidth = metrics.charWidth(word.charAt(index));
                if (currentLineWidth > 0 && currentLineWidth + characterWidth > availableWidth) {
                    rows++;
                    currentLineWidth = 0;
                }
                currentLineWidth += characterWidth;
            }
        }
        if (selectedResourceField.getRows() != rows) {
            selectedResourceField.setRows(rows);
            int height = metrics.getHeight() * rows + insets.top + insets.bottom;
            selectedResourceField.setPreferredSize(new Dimension(100, height));
            selectedResourceField.revalidate();
        }
    }

    private static void setColumnWidths(JTable table, int... widths) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int column = 0; column < widths.length && column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }

    private void checkAvailability() {
        try {
            boolean available = bookingService.isAvailable(requireSelectedResource(), startDateTime, endDateTime);
            showFeedback(available ? "Available for this time slot." : "Already reserved during this time slot.",
                    available ? TEAL : new Color(181, 77, 64));
        } catch (IllegalArgumentException | BookingPersistenceException exception) {
            showFeedback(exception.getMessage(), new Color(181, 77, 64));
        }
    }

    private void createReservation() {
        String resourceId;
        try {
            resourceId = requireSelectedResource();
        } catch (IllegalArgumentException exception) {
            showFeedback(exception.getMessage(), new Color(181, 77, 64));
            return;
        }
        String borrowerName = borrowerField.getText();
        LocalDateTime start = startDateTime;
        LocalDateTime end = endDateTime;
        runDatabaseTask("Saving reservation…", () ->
                bookingService.reserve(resourceId, borrowerName, start, end), reservation -> {
            if (borrowerField.getText().equals(borrowerName)) borrowerField.setText("");
            loadReservations();
            filterResources();
            showFeedback("Reservation created · " + reservation.getId(), TEAL);
        }, exception -> showFeedback(messageOf(exception), new Color(181, 77, 64)));
    }

    private void cancelReservation() {
        int selectedRow = reservationTable.getSelectedRow();
        if (selectedRow < 0) return;
        String reservationId = (String) reservationModel.getValueAt(
                reservationTable.convertRowIndexToModel(selectedRow), 0);
        runDatabaseTask("Cancelling reservation…", () -> bookingService.cancel(reservationId), cancelled -> {
            if (cancelled) {
                loadReservations();
                filterResources();
                showFeedback("Reservation " + reservationId + " cancelled.", TEAL);
            } else {
                showFeedback("That reservation is no longer active.", new Color(181, 77, 64));
            }
        }, exception -> showFeedback(messageOf(exception), new Color(181, 77, 64)));
    }

    private void loadReservations() {
        reservationModel.setReservations(bookingService.getReservations());
        cancelButton.setEnabled(false);
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

    private String requireSelectedResource() {
        if (selectedResourceId == null) {
            throw new IllegalArgumentException("Select a resource from the list first.");
        }
        return selectedResourceId;
    }

    private void chooseDateTime(boolean start) {
        LocalDateTime current = start ? startDateTime : endDateTime;
        String title = start ? "Choose start date and time" : "Choose end date and time";
        DateTimePickerDialog.choose(this, title, current).ifPresent(value -> {
            if (start && !value.isAfter(LocalDateTime.now())) {
                showFeedback("Reservation start time must be in the future.", new Color(181, 77, 64));
                return;
            }
            if (start && !endDateTime.isAfter(value)) {
                showFeedback("Start time must be before the end time.", new Color(181, 77, 64));
                return;
            }
            if (!start && !value.isAfter(startDateTime)) {
                showFeedback("End time must be after the start time.", new Color(181, 77, 64));
                return;
            }
            if (start) {
                startDateTime = value;
                updateDateButton(startButton, value);
            } else {
                endDateTime = value;
                updateDateButton(endButton, value);
            }
            showFeedback(" ", MUTED);
            filterResources();
        });
    }

    private static void updateDateButton(JButton button, LocalDateTime value) {
        button.setText(value.format(PICKER_FORMAT));
    }

    private void showFeedback(String message, Color color) {
        feedbackField.setText(message);
        feedbackField.setForeground(color);
    }

    private static JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        return panel;
    }

    private static JPanel sectionHeading(String titleText, String subtitleText) {
        JPanel heading = new JPanel(new BorderLayout(0, 3));
        heading.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(INK);
        JLabel subtitle = new JLabel(subtitleText);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(MUTED);
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.SOUTH);
        return heading;
    }

    private static JScrollPane tableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(Color.WHITE);
        ScrollBarStyler.style(scroll);
        return scroll;
    }

    private static void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setForeground(INK);
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(237, 241, 243));
        table.setSelectionBackground(new Color(220, 240, 238));
        table.setSelectionForeground(INK);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
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

    private static void styleInput(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setPreferredSize(new Dimension(100, 38));
    }

    private static void styleButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.setRolloverEnabled(false);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private static void stylePrimaryButton(JButton button) {
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

    private static void styleDateButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
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

    private static void addFormLabel(JPanel panel, GridBagConstraints constraints, int row, String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
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
