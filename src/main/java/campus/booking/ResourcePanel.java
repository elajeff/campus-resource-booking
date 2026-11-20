package campus.booking;

import static campus.booking.UiStyles.*;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

/** The searchable resource catalog with the add and retire buttons. */
final class ResourcePanel extends JPanel {
    private final BookingService bookingService;
    private final ResourceTableModel model;
    private final JTable table;
    private final TableRowSorter<ResourceTableModel> sorter;
    private final JTextField searchField = new JTextField();
    private final JCheckBox availableOnlyCheck = new JCheckBox("Available for selected time");
    private final JLabel countLabel = new JLabel();
    private final JButton retireButton = new JButton("Retire selected");
    private final Consumer<Resource> onSelectionChanged;
    private Resource selectedResource;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;

    ResourcePanel(BookingService bookingService, Runnable onAdd, Runnable onRetire,
                  Consumer<Resource> onSelectionChanged) {
        this.bookingService = bookingService;
        this.onSelectionChanged = onSelectionChanged;
        model = new ResourceTableModel(bookingService.getResources());
        table = new JTable(model);
        sorter = new TableRowSorter<>(model);

        styleTable(table);
        setColumnWidths(table, 58, 105, 245, 155, 86);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                select(selectedRow < 0 ? null : model.getResource(table.convertRowIndexToModel(selectedRow)));
            }
        });

        styleCard(this);
        setLayout(new BorderLayout(0, 12));
        setMinimumSize(new Dimension(360, 0));
        add(createHeading(onAdd, onRetire), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        searchField.setPreferredSize(new Dimension(200, 36));
        styleInput(searchField);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { filter(); }
            public void removeUpdate(DocumentEvent event) { filter(); }
            public void changedUpdate(DocumentEvent event) { filter(); }
        });
        availableOnlyCheck.setFont(SMALL_FONT);
        availableOnlyCheck.setForeground(INK);
        availableOnlyCheck.setOpaque(false);
        availableOnlyCheck.addActionListener(event -> filter());
        JPanel filters = new JPanel(new BorderLayout(10, 0));
        filters.setOpaque(false);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setLabelFor(searchField);
        searchLabel.setForeground(INK);
        searchField.setToolTipText("Search by name, ID, type, or location");
        filters.add(searchLabel, BorderLayout.WEST);
        filters.add(searchField, BorderLayout.CENTER);
        filters.add(availableOnlyCheck, BorderLayout.EAST);
        body.add(filters, BorderLayout.NORTH);
        body.add(tableScroll(table), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JPanel createHeading(Runnable onAdd, Runnable onRetire) {
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JPanel labels = new JPanel(new BorderLayout(0, 3));
        labels.setOpaque(false);
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        countLabel.setForeground(INK);
        countLabel.setText("Resources (" + model.getRowCount() + ")");
        JLabel subtitle = new JLabel("Search the catalog or add and retire inventory.");
        subtitle.setFont(SMALL_FONT);
        subtitle.setForeground(MUTED);
        labels.add(countLabel, BorderLayout.NORTH);
        labels.add(subtitle, BorderLayout.SOUTH);
        heading.add(labels, BorderLayout.CENTER);

        JButton addButton = new JButton("Add resource");
        stylePrimaryButton(addButton);
        addButton.setFont(SMALL_BOLD_FONT);
        addButton.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        addButton.addActionListener(event -> onAdd.run());
        styleButton(retireButton);
        retireButton.setEnabled(false);
        retireButton.addActionListener(event -> onRetire.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(addButton);
        actions.add(retireButton);
        heading.add(actions, BorderLayout.EAST);
        return heading;
    }

    Resource selectedResource() {
        return selectedResource;
    }

    /** Sets the time slot used by "Available for selected time". The end must be after the start. */
    void setTimeSlot(LocalDateTime start, LocalDateTime end) {
        slotStart = start;
        slotEnd = end;
        filter();
    }

    /** Shows the current catalog and keeps the selected row if that resource is still listed. */
    void reload() {
        String previousId = selectedResource == null ? null : selectedResource.getId();
        model.setResources(bookingService.getResources());
        filter();
        if (previousId != null) {
            for (int modelRow = 0; modelRow < model.getRowCount(); modelRow++) {
                if (model.getResource(modelRow).getId().equalsIgnoreCase(previousId)) {
                    int viewRow = table.convertRowIndexToView(modelRow);
                    if (viewRow >= 0) {
                        table.setRowSelectionInterval(viewRow, viewRow);
                        return;
                    }
                    break;
                }
            }
        }
        select(null);
    }

    private void select(Resource resource) {
        selectedResource = resource;
        retireButton.setEnabled(resource != null);
        onSelectionChanged.accept(resource);
    }

    private void filter() {
        Set<String> availableIds = null;
        if (availableOnlyCheck.isSelected()) {
            availableIds = new HashSet<>();
            for (Resource resource : bookingService.availableResources(slotStart, slotEnd)) {
                availableIds.add(resource.getId().toLowerCase(Locale.ROOT));
            }
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        Set<String> matches = availableIds;
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends ResourceTableModel, ? extends Integer> entry) {
                Resource resource = model.getResource(entry.getIdentifier());
                boolean matchesSearch = query.isEmpty()
                        || resource.getId().toLowerCase(Locale.ROOT).contains(query)
                        || resource.getType().toString().toLowerCase(Locale.ROOT).contains(query)
                        || resource.getName().toLowerCase(Locale.ROOT).contains(query)
                        || resource.getLocation().toLowerCase(Locale.ROOT).contains(query);
                return matchesSearch && (matches == null
                        || matches.contains(resource.getId().toLowerCase(Locale.ROOT)));
            }
        });
        boolean filtered = availableOnlyCheck.isSelected() || !query.isEmpty();
        countLabel.setText("Resources (" + table.getRowCount() + (filtered ? " shown)" : ")"));
    }
}
