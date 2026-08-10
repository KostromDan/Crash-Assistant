package dev.kostromdan.mods.crash_assistant.app.gui.modlist.history;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListComparison;
import dev.kostromdan.mods.crash_assistant.app.gui.modlist.ModListDiffDialog;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListTxtParser;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListComparisonReference;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryRecord;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryManager;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStatus;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStore;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two-pane selector for comparing arbitrary mod-list launch snapshots. */
public final class ModListHistoryDialog extends JDialog {
    private static final AtomicBoolean OPENING_OR_OPEN = new AtomicBoolean(false);

    private final List<ModListHistoryRow> rows;
    private final ModListHistoryRow currentRow;
    private final EnumMap<ModListHistoryStatus, JCheckBox> filters =
            new EnumMap<ModListHistoryStatus, JCheckBox>(ModListHistoryStatus.class);
    private final ModListHistoryTable leftTable;
    private final ModListHistoryTable rightTable;
    private final JButton compareButton = new JButton(LanguageProvider.get("gui.modlist_history.compare"));
    private final String preferredReferenceId;

    private ModListHistoryDialog(Window owner, LoadedRows loadedRows) {
        super(owner, LanguageProvider.get("gui.modlist_history.title"), Dialog.ModalityType.APPLICATION_MODAL);
        CrashAssistantGUI.setUpIcon(this);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        rows = loadedRows.rows;
        currentRow = rows.get(0);
        preferredReferenceId = loadedRows.preferredReferenceId;
        leftTable = createTable();
        rightTable = createTable();

        buildUi();
        setMinimumSize(new Dimension(760, 420));
        setSize(new Dimension(1040, 580));
        setLocationRelativeTo(owner);
        selectInitialRows();
    }

    /** Reloads history and opens a fresh selector. Safe to call from any thread. */
    public static void showDialog(final Window parent) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showDialog(parent);
                }
            });
            return;
        }
        if (!OPENING_OR_OPEN.compareAndSet(false, true)) {
            return;
        }
        final Window owner = parent == null ? CrashAssistantGUI.getFrame() : parent;
        if (owner != null) {
            owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        Thread loader = new Thread(new Runnable() {
            @Override
            public void run() {
                LoadedRows loaded = null;
                Exception loadFailure = null;
                try {
                    loaded = loadRows();
                } catch (Exception e) {
                    loadFailure = e;
                }
                final LoadedRows loadedRows = loaded;
                final Exception failure = loadFailure;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (owner != null) {
                            owner.setCursor(Cursor.getDefaultCursor());
                        }
                        if (failure != null) {
                            OPENING_OR_OPEN.set(false);
                            CrashAssistantApp.LOGGER.error("Failed to load mod-list history", failure);
                            if (owner == null || owner.isDisplayable()) {
                                String message = LanguageProvider.get("gui.modlist_history.load_error")
                                        .replace("$ERROR$", failure.getMessage() == null
                                                ? failure.getClass().getSimpleName()
                                                : failure.getMessage());
                                JOptionPane.showMessageDialog(
                                        owner,
                                        message,
                                        LanguageProvider.get("gui.modlist_history.title"),
                                        JOptionPane.ERROR_MESSAGE);
                            }
                            return;
                        }
                        if (owner != null && !owner.isDisplayable()) {
                            OPENING_OR_OPEN.set(false);
                            return;
                        }
                        try {
                            new ModListHistoryDialog(owner, loadedRows).setVisible(true);
                        } catch (RuntimeException e) {
                            CrashAssistantApp.LOGGER.error("Failed to open mod-list history", e);
                        } finally {
                            OPENING_OR_OPEN.set(false);
                        }
                    }
                });
            }
        }, "modlist-history-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private static LoadedRows loadRows() {
        ModListHistoryStore store = ModListHistoryManager.getStore();
        Optional<ModListHistoryRecord> currentRecord = ModListHistoryManager.getCurrentRecord();
        long currentTimestamp = currentRecord.isPresent()
                ? currentRecord.get().getTimestamp()
                : System.currentTimeMillis();

        ModListHistoryRow syntheticCurrent;
        if (currentRecord.isPresent() && !currentRecord.get().isLegacySnapshot()) {
            ModListHistoryRecord record = currentRecord.get();
            syntheticCurrent = ModListHistoryRow.current(
                    currentTimestamp, record.getStatus(), record.getMods());
        } else {
            syntheticCurrent = ModListHistoryRow.current(
                    currentTimestamp,
                    ModListHistoryStatus.STARTED,
                    ModListUtils.getCurrentModList(true));
        }

        List<ModListHistoryRow> result = new ArrayList<ModListHistoryRow>();
        result.add(syntheticCurrent);

        List<ModListHistoryRecord> records = store.listRecordsNewestFirst();
        // Keep migration snapshots immediately below Current. They are honest,
        // unclassified manual baselines rather than a fabricated launch status.
        for (ModListHistoryRecord record : records) {
            if (record.isLegacySnapshot()) {
                result.add(ModListHistoryRow.history(record));
            }
        }
        for (ModListHistoryRecord record : records) {
            if (record.isLegacySnapshot() || record.getTimestamp() == currentTimestamp) {
                continue;
            }
            result.add(ModListHistoryRow.history(record));
        }
        String preferredId = null;
        if (currentRecord.isPresent()) {
            Optional<ModListComparisonReference> reference = store
                    .findComparisonReference(currentRecord.get(), records);
            if (reference.isPresent()) {
                preferredId = ModListHistoryRow.history(reference.get().getRecord()).getStableId();
            }
        }
        return new LoadedRows(result, preferredId);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(createFilterPanel(), BorderLayout.NORTH);

        JScrollPane leftScroll = new JScrollPane(leftTable);
        leftScroll.setBorder(BorderFactory.createTitledBorder(LanguageProvider.get("gui.modlist_history.left")));
        JScrollPane rightScroll = new JScrollPane(rightTable);
        rightScroll.setBorder(BorderFactory.createTitledBorder(LanguageProvider.get("gui.modlist_history.right")));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
        root.add(splitPane, BorderLayout.CENTER);

        JButton importButton = new JButton(LanguageProvider.get("gui.modlist_history.compare_to_txt"));
        importButton.addActionListener(event -> showImportChoice());

        compareButton.setEnabled(false);
        compareButton.addActionListener(event -> compareSelected());
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(importButton, BorderLayout.WEST);
        JPanel comparePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        comparePanel.add(compareButton);
        footer.add(comparePanel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel createFilterPanel() {
        // Two predictable rows avoid FlowLayout clipping long localized status
        // names (notably Russian) at the fixed dialog width.
        JPanel panel = new JPanel(new GridLayout(0, 3, 10, 2));
        for (ModListHistoryStatus status : ModListHistoryStatus.values()) {
            JCheckBox checkBox = new JCheckBox(statusLabel(status), true);
            checkBox.addItemListener(event -> applyFilters());
            filters.put(status, checkBox);
            panel.add(checkBox);
        }
        return panel;
    }

    private ModListHistoryTable createTable() {
        ModListHistoryTableModel model = new ModListHistoryTableModel(rows);
        ModListHistoryTable table = new ModListHistoryTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(Math.max(24, table.getRowHeight()));
        table.setAutoCreateRowSorter(false);

        final TableRowSorter<ModListHistoryTableModel> sorter =
                new TableRowSorter<ModListHistoryTableModel>(model);
        sorter.setComparator(ModListHistoryTableModel.STATUS_COLUMN, new Comparator<ModListHistoryRow>() {
            @Override
            public int compare(ModListHistoryRow left, ModListHistoryRow right) {
                return Integer.compare(left.getStatusSortOrder(), right.getStatusSortOrder());
            }
        });
        sorter.setComparator(ModListHistoryTableModel.DATE_COLUMN, new Comparator<ModListHistoryRow>() {
            @Override
            public int compare(ModListHistoryRow left, ModListHistoryRow right) {
                long leftValue = left.isCurrent() ? Long.MAX_VALUE : left.getTimestamp();
                long rightValue = right.isCurrent() ? Long.MAX_VALUE : right.getTimestamp();
                return Long.compare(leftValue, rightValue);
            }
        });
        sorter.setSortKeys(Collections.singletonList(
                new javax.swing.RowSorter.SortKey(ModListHistoryTableModel.DATE_COLUMN, SortOrder.DESCENDING)));
        table.setRowSorter(sorter);

        table.getColumnModel().getColumn(ModListHistoryTableModel.STATUS_COLUMN)
                .setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(ModListHistoryTableModel.DATE_COLUMN)
                .setCellRenderer(new DateRenderer());
        DefaultTableCellRenderer countRenderer = new DefaultTableCellRenderer();
        countRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(ModListHistoryTableModel.MODS_COUNT_COLUMN)
                .setCellRenderer(countRenderer);
        table.getColumnModel().getColumn(ModListHistoryTableModel.MODS_COUNT_COLUMN).setMaxWidth(110);
        table.getSelectionModel().addListSelectionListener(event -> updateCompareButton());
        return table;
    }

    private void applyFilters() {
        RowFilter<ModListHistoryTableModel, Integer> filter =
                new RowFilter<ModListHistoryTableModel, Integer>() {
                    @Override
                    public boolean include(Entry<? extends ModListHistoryTableModel, ? extends Integer> entry) {
                        ModListHistoryRow row = entry.getModel().getRow(entry.getIdentifier().intValue());
                        if (row.isCurrent() || row.isLegacy()) {
                            return true;
                        }
                        JCheckBox checkBox = filters.get(row.getStatus());
                        return checkBox != null && checkBox.isSelected();
                    }
                };
        sorter(leftTable).setRowFilter(filter);
        sorter(rightTable).setRowFilter(filter);
        updateCompareButton();
    }

    @SuppressWarnings("unchecked")
    private static TableRowSorter<ModListHistoryTableModel> sorter(JTable table) {
        return (TableRowSorter<ModListHistoryTableModel>) table.getRowSorter();
    }

    private void selectInitialRows() {
        int currentRight = findViewRow(rightTable, currentRow.getStableId());
        if (currentRight >= 0) {
            rightTable.setRowSelectionInterval(currentRight, currentRight);
        }

        if (preferredReferenceId != null) {
            int preferredLeft = findViewRow(leftTable, preferredReferenceId);
            if (preferredLeft >= 0) {
                leftTable.setRowSelectionInterval(preferredLeft, preferredLeft);
                updateCompareButton();
                return;
            }
        }
        for (ModListHistoryRow row : rows) {
            if (row.isCurrent()) {
                continue;
            }
            int left = findViewRow(leftTable, row.getStableId());
            if (left >= 0) {
                leftTable.setRowSelectionInterval(left, left);
                break;
            }
        }
        updateCompareButton();
    }

    private static int findViewRow(JTable table, String stableId) {
        ModListHistoryTableModel model = (ModListHistoryTableModel) table.getModel();
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getRow(i).getStableId().equals(stableId)) {
                return table.convertRowIndexToView(i);
            }
        }
        return -1;
    }

    private static final class LoadedRows {
        private final List<ModListHistoryRow> rows;
        private final String preferredReferenceId;

        private LoadedRows(List<ModListHistoryRow> rows, String preferredReferenceId) {
            this.rows = rows;
            this.preferredReferenceId = preferredReferenceId;
        }
    }

    private void updateCompareButton() {
        ModListHistoryRow left = selected(leftTable);
        ModListHistoryRow right = selected(rightTable);
        compareButton.setEnabled(left != null && right != null
                && !left.getStableId().equals(right.getStableId()));
    }

    private static ModListHistoryRow selected(JTable table) {
        int viewIndex = table.getSelectedRow();
        if (viewIndex < 0) {
            return null;
        }
        int modelIndex = table.convertRowIndexToModel(viewIndex);
        return ((ModListHistoryTableModel) table.getModel()).getRow(modelIndex);
    }

    private void compareSelected() {
        ModListHistoryRow left = selected(leftTable);
        ModListHistoryRow right = selected(rightTable);
        if (left == null || right == null || left.getStableId().equals(right.getStableId())) {
            return;
        }

        // The history browser is an explorer, not a file-management surface.
        // Arbitrary old/crashed snapshots must never enable live remove/revert
        // actions merely because the other selected row happens to be Current.
        ModListComparison comparison = ModListComparison.readOnly(
                LanguageProvider.get("gui.modlist_history.comparison_title"),
                rowLabel(left), sourceKind(left), left.getMods(),
                rowLabel(right), sourceKind(right), right.getMods());
        ModListDiffDialog.showDialog(this, comparison);
    }

    private static ModListComparison.SourceKind sourceKind(ModListHistoryRow row) {
        if (row.isCurrent()) {
            return ModListComparison.SourceKind.CURRENT;
        }
        return row.isLegacy()
                ? ModListComparison.SourceKind.LEGACY_SNAPSHOT
                : ModListComparison.SourceKind.HISTORY;
    }

    private void showImportChoice() {
        final JDialog choice = new JDialog(
                this,
                LanguageProvider.get("gui.modlist_history.import.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        CrashAssistantGUI.setUpIcon(choice);
        choice.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JButton clipboard = largeButton(LanguageProvider.get("gui.modlist_history.import.from_clipboard"));
        clipboard.addActionListener(event -> {
            try {
                Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                        .getData(DataFlavor.stringFlavor);
                importText(choice, data == null ? null : data.toString());
            } catch (Exception e) {
                showImportError(choice, e);
            }
        });

        JButton file = largeButton(LanguageProvider.get("gui.modlist_history.import.from_file"));
        file.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(LanguageProvider.get("gui.modlist_history.import.from_file"));
            chooser.setFileFilter(new FileNameExtensionFilter("modlist.txt (*.txt)", "txt"));
            if (chooser.showOpenDialog(choice) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                LinkedHashSet<Mod> imported = ModListTxtParser.parse(chooser.getSelectedFile().toPath());
                openImportedComparison(choice, imported);
            } catch (Exception e) {
                showImportError(choice, e);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 18));
        buttons.add(clipboard);
        buttons.add(file);
        choice.setContentPane(buttons);
        choice.pack();
        choice.setResizable(false);
        choice.setLocationRelativeTo(this);
        choice.setVisible(true);
    }

    private static JButton largeButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(220, 78));
        Font font = button.getFont();
        button.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + 1.0f));
        return button;
    }

    private void importText(JDialog choice, String text) {
        try {
            openImportedComparison(choice, ModListTxtParser.parse(text));
        } catch (Exception e) {
            showImportError(choice, e);
        }
    }

    private void openImportedComparison(JDialog choice, final LinkedHashSet<Mod> imported) {
        choice.dispose();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ModListComparison comparison = ModListComparison.readOnly(
                        LanguageProvider.get("gui.modlist_history.comparison_title"),
                        LanguageProvider.get("gui.modlist_history.import.imported"),
                        ModListComparison.SourceKind.IMPORTED_MODLIST,
                        imported,
                        LanguageProvider.get("gui.modlist_history.current"),
                        ModListComparison.SourceKind.CURRENT,
                        currentRow.getMods());
                ModListDiffDialog.showDialog(ModListHistoryDialog.this, comparison);
            }
        });
    }

    private static void showImportError(Component parent, Exception error) {
        String message = LanguageProvider.get("gui.modlist_history.import.error")
                .replace("$ERROR$", error.getMessage() == null
                        ? error.getClass().getSimpleName()
                        : error.getMessage());
        JOptionPane.showMessageDialog(
                parent,
                message,
                LanguageProvider.get("gui.modlist_history.import.title"),
                JOptionPane.ERROR_MESSAGE);
    }

    private static String rowLabel(ModListHistoryRow row) {
        if (row.isCurrent()) {
            return LanguageProvider.get("gui.modlist_history.current");
        }
        if (row.isLegacy()) {
            return LanguageProvider.get("gui.modlist_history.legacy_snapshot");
        }
        return statusLabel(row.getStatus()) + " — " + formatDate(row.getTimestamp());
    }

    private static String statusLabel(ModListHistoryStatus status) {
        if (status == null) {
            return LanguageProvider.get("gui.modlist_history.legacy_snapshot");
        }
        switch (status) {
            case STARTED:
                return LanguageProvider.get("gui.modlist_history.status.started");
            case TITLE_SCREEN:
                return LanguageProvider.get("gui.modlist_history.status.title_screen");
            case JOINED:
                return LanguageProvider.get("gui.modlist_history.status.joined");
            case CRASHED_DURING_GAMEPLAY:
                return LanguageProvider.get("gui.modlist_history.status.crashed_during_gameplay");
            case CLOSED_WITHOUT_CRASH:
                return LanguageProvider.get("gui.modlist_history.status.closed_without_crash");
            default:
                throw new IllegalArgumentException("Unknown status " + status);
        }
    }

    private static String formatDate(long timestamp) {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(new Date(timestamp));
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            ModListHistoryRow row = (ModListHistoryRow) value;
            setText(row.isLegacy()
                    ? LanguageProvider.get("gui.modlist_history.legacy_snapshot")
                    : statusLabel(row.getStatus()));
        }
    }

    private static final class DateRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            ModListHistoryRow row = (ModListHistoryRow) value;
            setText(row.isCurrent()
                    ? LanguageProvider.get("gui.modlist_history.current")
                    : formatDate(row.getTimestamp()));
            setFont(getFont().deriveFont(row.isCurrent() ? Font.BOLD : Font.PLAIN));
        }
    }

    private static final class ModListHistoryTable extends JTable {
        ModListHistoryTable(ModListHistoryTableModel model) {
            super(model);
        }

        @Override
        public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            Component component = super.prepareRenderer(renderer, row, column);
            if (isRowSelected(row)) {
                component.setBackground(getSelectionBackground());
                component.setForeground(getSelectionForeground());
                return component;
            }

            int modelIndex = convertRowIndexToModel(row);
            ModListHistoryRow historyRow = ((ModListHistoryTableModel) getModel()).getRow(modelIndex);
            Color background = getBackground();
            Color accent = accent(historyRow);
            component.setBackground(accent == null ? background : blend(background, accent));
            component.setForeground(getForeground());
            return component;
        }

        private static Color accent(ModListHistoryRow row) {
            if (row.isLegacy() || row.getStatus() == null) {
                return null;
            }
            switch (row.getStatus()) {
                case STARTED:
                    return new Color(220, 65, 65);
                case TITLE_SCREEN:
                    return new Color(225, 155, 40);
                case JOINED:
                    return new Color(55, 160, 85);
                case CRASHED_DURING_GAMEPLAY:
                    return new Color(165, 75, 185);
                case CLOSED_WITHOUT_CRASH:
                    return new Color(50, 145, 205);
                default:
                    return null;
            }
        }

        private static Color blend(Color base, Color accent) {
            double luminance = 0.2126 * base.getRed() + 0.7152 * base.getGreen() + 0.0722 * base.getBlue();
            double accentWeight = luminance < 128 ? 0.38 : 0.22;
            double baseWeight = 1.0 - accentWeight;
            return new Color(
                    (int) Math.round(base.getRed() * baseWeight + accent.getRed() * accentWeight),
                    (int) Math.round(base.getGreen() * baseWeight + accent.getGreen() * accentWeight),
                    (int) Math.round(base.getBlue() * baseWeight + accent.getBlue() * accentWeight));
        }
    }
}
