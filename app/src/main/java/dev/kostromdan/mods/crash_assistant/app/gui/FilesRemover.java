package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.utils.ThemeUtils;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FilesRemover — modal Swing dialog for disabling/removing specific files.
 * <p>
 * Key points:
 * - Two modes:
 * JAR: Disable/Enable (rename <name> -> <name>.disabled) + Remove + Show in Explorer
 * CONFIG: Open (associated app) + Remove + Show in Explorer
 * - Global actions at the bottom operate on checked rows.
 * - Header has a master checkbox (select/deselect all).
 * - Column widths adapt to the actual localized text (no hardcoded widths).
 * - "File" column is the weak/elastic one; it gets clipped first and shows tooltip with full path.
 * - When a JAR entry is disabled (ends with ".disabled"), the file name text is rendered RED.
 * - Display-name suffix stays in sync with the actual file name.
 * <p>
 * DEMO LAUNCHER (main):
 * - Two buttons:
 * * Open Mods (JAR mode): lists real files from ./mods (non-recursive)
 * * Open Configs (CONFIG mode): lists real files from ./config (recursive)
 * <p>
 * Usage:
 * FilesRemover.showDialog(parentWindow, List<Path>, Mode)
 * FilesRemover.showDialog(parentWindow, Map<String, Path>, Mode) // custom display name per path
 */
public class FilesRemover extends JDialog {

    /**
     * Operation mode.
     */
    public enum Mode {JAR, CONFIG}

    @FunctionalInterface
    public interface CustomAction {
        void execute(FilesRemover dialog) throws Exception;
    }

    @FunctionalInterface
    public interface CustomDeleteAction {
        boolean execute(FilesRemover dialog) throws Exception;
    }

    public static class CustomEntry {
        private final Path path;
        private final String displayName;
        private final CustomAction openAction;
        private final CustomDeleteAction deleteAction;
        private final CustomAction revealAction;

        public CustomEntry(Path path,
                           String displayName,
                           CustomAction openAction,
                           CustomDeleteAction deleteAction,
                           CustomAction revealAction) {
            this.path = path;
            this.displayName = displayName;
            this.openAction = openAction;
            this.deleteAction = deleteAction;
            this.revealAction = revealAction;
        }
    }

    /**
     * Open the dialog with display names derived from file names.
     */
    public static void showDialog(Window parent, List<Path> paths, Mode mode) {
        List<Row> rows = paths.stream().map(p -> new Row(p, displayNameFrom(p), mode)).collect(Collectors.toList());
        FilesRemover dlg = new FilesRemover(parent, rows, mode, null);
        dlg.setVisible(true);
    }

    /**
     * Open the dialog with explicit display names (key) and backing paths (value).
     */
    public static void showDialog(Window parent, Map<String, Path> displayNameToPath, Mode mode) {
        List<Row> rows = displayNameToPath.entrySet().stream()
                .map(e -> new Row(e.getValue(), e.getKey(), mode))
                .collect(Collectors.toList());
        FilesRemover dlg = new FilesRemover(parent, rows, mode, null);
        dlg.setVisible(true);
    }

    /**
     * Open the dialog with custom actions for open/remove/reveal.
     */
    public static void showDialog(Window parent, List<CustomEntry> entries, Mode mode, String descriptionText) {
        List<Row> rows = entries.stream()
                .map(e -> new Row(e.path, e.displayName, mode, e.openAction, e.deleteAction, e.revealAction))
                .collect(Collectors.toList());
        FilesRemover dlg = new FilesRemover(parent, rows, mode, descriptionText);
        dlg.setVisible(true);
    }

    // ------------ Row & Model ------------

    /**
     * One table row: selection state, display name, and actual path.
     */
    private static class Row {
        boolean selected;
        Path path;                 // may change (Disable/Enable renames)
        String displayName;        // shown in the table; must END WITH the actual file name
        final Mode mode;
        final CustomAction openAction;
        final CustomDeleteAction deleteAction;
        final CustomAction revealAction;

        Row(Path p, String displayName, Mode m) {
            this(p, displayName, m, null, null, null);
        }

        Row(Path p,
            String displayName,
            Mode m,
            CustomAction openAction,
            CustomDeleteAction deleteAction,
            CustomAction revealAction) {
            this.path = p;
            this.displayName = displayName;
            this.mode = m;
            this.openAction = openAction;
            this.deleteAction = deleteAction;
            this.revealAction = revealAction;
        }

        boolean isDisabledJar() {
            return mode == Mode.JAR && getFileName(path).endsWith(".disabled");
        }
    }

    /**
     * Table model.
     */
    private static class Model extends AbstractTableModel {
        final String[] columns;
        final List<Row> rows;
        final Mode mode;

        Model(List<Row> rows, Mode mode) {
            this.rows = rows;
            this.mode = mode;
            this.columns = new String[]{
                    "", LanguageProvider.get("gui.files_remover.column.file"),
                    (mode == Mode.JAR ? LanguageProvider.get("gui.files_remover.column.toggle") : LanguageProvider.get("gui.files_remover.column.open")),
                    LanguageProvider.get("gui.files_remover.column.remove"), LanguageProvider.get("gui.show_in_explorer_button")
            };
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int c) {
            return columns[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c != 1;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            switch (c) {
                case 0:
                    return row.selected;
                case 1:
                    return row.displayName;
                case 2:
                    return (row.mode == Mode.JAR ? (row.isDisabledJar() ? LanguageProvider.get("gui.files_remover.enable") : LanguageProvider.get("gui.files_remover.disable")) : LanguageProvider.get("gui.files_remover.open"));
                case 3:
                    return LanguageProvider.get("gui.files_remover.remove");
                case 4:
                    return LanguageProvider.get("gui.show");
            }
            return null;
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) {
                rows.get(r).selected = (Boolean) v;
                fireTableRowsUpdated(r, r);
            }
        }

        void removeRow(int index) {
            rows.remove(index);
            fireTableRowsDeleted(index, index);
        }

        void refreshRow(int index) {
            fireTableRowsUpdated(index, index);
        }

        boolean allSelected() {
            if (rows.isEmpty()) return false;
            for (Row r : rows) if (!r.selected) return false;
            return true;
        }

        void setAllSelected(boolean sel) {
            for (int i = 0; i < rows.size(); i++) rows.get(i).selected = sel;
            fireTableDataChanged();
        }
    }

    // ------------ Renderers & Editors ------------

    /**
     * Simple button renderer for action columns.
     */
    private static class ButtonRenderer extends JButton implements TableCellRenderer {
        ButtonRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            setText(String.valueOf(v));
            return this;
        }
    }

    /**
     * Button editor executing an action on click.
     */
    private static abstract class ButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        protected final JButton button = new JButton();
        protected final JTable table;
        protected int row, col;
        protected Object label;

        ButtonEditor(JTable table) {
            this.table = table;
            button.addActionListener(this);
        }

        @Override
        public Component getTableCellEditorComponent(JTable t, Object v, boolean s, int r, int c) {
            this.row = r;
            this.col = c;
            this.label = v;
            button.setText(String.valueOf(v));
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return label;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            onClick(row, col);
            fireEditingStopped();
        }

        protected abstract void onClick(int row, int col);
    }

    /**
     * Renderer for the "File" column: red text if disabled in JAR mode.
     */
    private class FileNameRenderer extends DefaultTableCellRenderer {
        private final Color defaultColor = UIManager.getColor("Table.foreground");
        private final Color disabledColor = new Color(180, 0, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            Row r = model.rows.get(modelRow);
            c.setForeground((r.mode == Mode.JAR && r.isDisabledJar()) ? disabledColor : defaultColor);
            setText(value == null ? "" : String.valueOf(value));
            return c;
        }
    }

    // ------------ File operations & error handling ------------

    /**
     * Show an error dialog.
     */
    private void showError(String title, String message, Exception ex) {
        String details = (ex == null ? "" : ("\n\nDetails:\n" + ex.getMessage()));
        JOptionPane.showMessageDialog(this, message + details, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Try to rename; return true if succeeded; otherwise show error and return false.
     */
    private boolean tryRename(Path from, Path to) {
        try {
            Files.move(from, to);
            return true;
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.rename_title"), String.format(LanguageProvider.get("gui.files_remover.error.rename_msg"), from, to), ex);
            return false;
        }
    }

    /**
     * Try to delete; return true if succeeded; otherwise show error and return false.
     */
    private boolean tryDelete(Path p) {
        try {
            Files.deleteIfExists(p);
            return true;
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.delete_title"), String.format(LanguageProvider.get("gui.files_remover.error.delete_msg"), p), ex);
            return false;
        }
    }

    /**
     * Try to open associated app; warn on failure.
     */
    private void tryOpen(Path p) {
        try {
            if (Files.exists(p)) Desktop.getDesktop().open(p.toFile());
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.open_title"), String.format(LanguageProvider.get("gui.files_remover.error.open_msg"), p), ex);
        }
    }

    /**
     * Try to reveal in file manager; warn on failure.
     */
    private void tryReveal(Path p) {
        try {
            if (!Files.exists(p)) return;
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win"))
                new ProcessBuilder("explorer.exe", "/select,", p.toAbsolutePath().toString()).start();
            else if (os.contains("mac")) new ProcessBuilder("open", "-R", p.toAbsolutePath().toString()).start();
            else {
                Path dir = Files.isDirectory(p) ? p : p.getParent();
                if (dir != null) new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString()).start();
            }
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.reveal_title"), String.format(LanguageProvider.get("gui.files_remover.error.reveal_msg"), p), ex);
        }
    }

    private static Path withDisabledSuffix(Path p) {
        String n = getFileName(p);
        return n.endsWith(".disabled") ? p : p.resolveSibling(n + ".disabled");
    }

    private static Path withoutDisabledSuffix(Path p) {
        String n = getFileName(p);
        return n.endsWith(".disabled") ? p.resolveSibling(n.substring(0, n.length() - ".disabled".length())) : p;
    }

    private static String displayNameFrom(Path p) {
        return getFileName(p);
    }

    private static String getFileName(Path p) {
        Path fn = p.getFileName();
        return fn == null ? p.toString() : fn.toString();
    }

    private boolean executeCustomOpen(Row row) {
        if (row.openAction == null) return false;
        try {
            row.openAction.execute(this);
            return true;
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.open_title"),
                    String.format(LanguageProvider.get("gui.files_remover.error.open_msg"), row.path),
                    ex);
            return false;
        }
    }

    private boolean executeCustomDelete(Row row) {
        if (row.deleteAction == null) return false;
        try {
            return row.deleteAction.execute(this);
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.delete_title"),
                    String.format(LanguageProvider.get("gui.files_remover.error.delete_msg"), row.path),
                    ex);
            return false;
        }
    }

    private boolean executeCustomReveal(Row row) {
        if (row.revealAction == null) return false;
        try {
            row.revealAction.execute(this);
            return true;
        } catch (Exception ex) {
            showError(LanguageProvider.get("gui.files_remover.error.reveal_title"),
                    String.format(LanguageProvider.get("gui.files_remover.error.reveal_msg"), row.path),
                    ex);
            return false;
        }
    }

    /**
     * Sync the trailing file-name portion of a display label after a rename.
     * Assumption (guaranteed by the caller): displayName ENDS WITH the actual file name prior to rename.
     * If that assumption fails at runtime (unexpected input), we fall back to using the bare newFileName.
     */
    private static void syncDisplayNameSuffix(Row row, String oldFileName, String newFileName) {
        String dn = row.displayName == null ? "" : row.displayName;
        if (dn.endsWith(oldFileName)) {
            row.displayName = dn.substring(0, dn.length() - oldFileName.length()) + newFileName;
        } else {
            // Fallback: preserve UI correctness even if the assumption was broken
            row.displayName = newFileName;
        }
    }

    // ------------ UI ------------

    private final JTable table;
    private final Model model;
    private final Mode mode;
    private final JScrollPane scroll;
    private final JCheckBox headerSelectAll = new JCheckBox(); // master checkbox in header

    // Make the global "Disable/Open Selected" button available to inner listeners.
    private JButton btnDisableOrOpen;

    private FilesRemover(Window parent, List<Row> rows, Mode mode, String descriptionText) {
        super(parent, LanguageProvider.get("gui.files_remover.title"), ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        this.model = new Model(rows, mode);

        this.table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                if (rowIndex >= 0 && colIndex == 1) {
                    Row r = model.rows.get(convertRowIndexToModel(rowIndex));
                    return r.path.toAbsolutePath().toString();
                }
                return super.getToolTipText(e);
            }

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return new Dimension(880, 380);
            }
        };
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // we control widths

        String effectiveDescription = (descriptionText != null && !descriptionText.trim().isEmpty())
                ? descriptionText
                : LanguageProvider.get("gui.files_remover.desc.intro") + "\n" +
                (mode == Mode.JAR
                        ? LanguageProvider.get("gui.files_remover.desc.jar")
                        : LanguageProvider.get("gui.files_remover.desc.config"));
        JTextArea desc = new JTextArea(effectiveDescription);
        desc.setEditable(false);
        desc.setBackground(UIManager.getColor("Panel.background"));
        desc.setBorder(new EmptyBorder(10, 10, 6, 10));

        // Columns: renderers & editors
        TableColumnModel cm = table.getColumnModel();
        ButtonRenderer btnRenderer = new ButtonRenderer();
        cm.getColumn(2).setCellRenderer(btnRenderer);
        cm.getColumn(3).setCellRenderer(btnRenderer);
        cm.getColumn(4).setCellRenderer(btnRenderer);

        // File column: red if disabled (JAR)
        cm.getColumn(1).setCellRenderer(new FileNameRenderer());

        // Editors
        cm.getColumn(2).setCellEditor(new ButtonEditor(table) {
            @Override
            protected void onClick(int viewRow, int col) {
                int r = table.convertRowIndexToModel(viewRow);
                Row row = model.rows.get(r);
                if (row.openAction != null) {
                    executeCustomOpen(row);
                    // no-op
                } else if (row.mode == Mode.JAR) {
                    String oldName = getFileName(row.path);
                    if (row.isDisabledJar()) {
                        Path t = withoutDisabledSuffix(row.path);
                        if (tryRename(row.path, t)) {
                            row.path = t;
                            String newName = getFileName(t);
                            syncDisplayNameSuffix(row, oldName, newName);
                            model.refreshRow(r);
                        }
                    } else {
                        Path t = withDisabledSuffix(row.path);
                        if (tryRename(row.path, t)) {
                            row.path = t;
                            String newName = getFileName(t);
                            syncDisplayNameSuffix(row, oldName, newName);
                            model.refreshRow(r);
                        }
                    }
                } else {
                    tryOpen(row.path);
                }
                layoutColumns(); // button text may have changed
                updateHeaderCheck();
                updateDisableOpenButtonLabel(btnDisableOrOpen); // keep label in sync with selection state
            }
        });
        cm.getColumn(3).setCellEditor(new ButtonEditor(table) {
            @Override
            protected void onClick(int viewRow, int col) {
                int r = table.convertRowIndexToModel(viewRow);
                Row row = model.rows.get(r);
                if ((row.deleteAction != null ? executeCustomDelete(row) : tryDelete(row.path))) {
                    model.removeRow(r);
                    layoutColumns();
                    updateHeaderCheck();
                    updateDisableOpenButtonLabel(btnDisableOrOpen);
                }
            }
        });
        cm.getColumn(4).setCellEditor(new ButtonEditor(table) {
            @Override
            protected void onClick(int viewRow, int col) {
                int r = table.convertRowIndexToModel(viewRow);
                Row row = model.rows.get(r);
                if (row.revealAction != null) {
                    executeCustomReveal(row);
                } else {
                    tryReveal(row.path);
                }
            }
        });

        // Master checkbox in header for column 0
        JTableHeader header = table.getTableHeader();
        headerSelectAll.setOpaque(false);
        headerSelectAll.setHorizontalAlignment(SwingConstants.CENTER);
        headerSelectAll.setToolTipText(LanguageProvider.get("gui.files_remover.select_all_tooltip"));

        cm.getColumn(0).setHeaderRenderer((table, value, isSelected, hasFocus, row, column) -> {
            // Get the standard header renderer component for styling reference
            TableCellRenderer defaultRenderer = header.getDefaultRenderer();
            Component headerComponent = defaultRenderer.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);

            // Create a new panel with the same styling
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(headerComponent.getBackground());
            panel.setForeground(headerComponent.getForeground());
            panel.setFont(headerComponent.getFont());
            if (headerComponent instanceof JComponent) {
                panel.setBorder(((JComponent) headerComponent).getBorder());
            }
            panel.setOpaque(true);

            // Configure and add checkbox
            headerSelectAll.setSelected(model.allSelected());
            headerSelectAll.setOpaque(false);
            panel.add(headerSelectAll, BorderLayout.CENTER);

            return panel;
        });
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col == 0 && SwingUtilities.isLeftMouseButton(e)) {
                    boolean target = !model.allSelected();
                    model.setAllSelected(target);
                    updateHeaderCheck();
                    updateDisableOpenButtonLabel(btnDisableOrOpen);
                }
            }
        });
        model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                updateHeaderCheck();
                updateDisableOpenButtonLabel(btnDisableOrOpen);
            }
        });

        // Bottom actions
        btnDisableOrOpen = new JButton(
                mode == Mode.JAR ? LanguageProvider.get("gui.files_remover.disable_selected")
                        : LanguageProvider.get("gui.files_remover.open_selected"));
        JButton btnRemove = new JButton(LanguageProvider.get("gui.files_remover.remove_selected"));
        JButton btnClose = new JButton(LanguageProvider.get("gui.close"));

        // Warn if nothing selected (both global buttons) and toggle Enable/Disable label if needed
        btnDisableOrOpen.addActionListener(e -> {
            List<Integer> idxs = selectedRowIndices();
            if (idxs.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        LanguageProvider.get("gui.files_remover.select_first_warning_body"),
                        LanguageProvider.get("gui.files_remover.select_first_warning_title"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (mode == Mode.JAR) {
                boolean enableMode = allSelectedDisabled(idxs);
                List<String> failures = new ArrayList<>();
                for (int i = idxs.size() - 1; i >= 0; i--) {
                    int r = idxs.get(i);
                    Row row = model.rows.get(r);
                    if (row.openAction != null) {
                        if (!executeCustomOpen(row)) failures.add(row.displayName);
                    } else if (enableMode) {
                        if (row.isDisabledJar()) {
                            String oldName = getFileName(row.path);
                            Path t = withoutDisabledSuffix(row.path);
                            if (tryRename(row.path, t)) {
                                row.path = t;
                                String newName = getFileName(t);
                                syncDisplayNameSuffix(row, oldName, newName);
                                model.refreshRow(r);
                            } else failures.add(row.displayName);
                        }
                    } else {
                        if (!row.isDisabledJar()) {
                            String oldName = getFileName(row.path);
                            Path t = withDisabledSuffix(row.path);
                            if (tryRename(row.path, t)) {
                                row.path = t;
                                String newName = getFileName(t);
                                syncDisplayNameSuffix(row, oldName, newName);
                                model.refreshRow(r);
                            } else failures.add(row.displayName);
                        }
                    }
                }
                if (!failures.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            String.format(LanguageProvider.get("gui.files_remover.partial_disable_failures"), String.join("\n", failures)),
                            LanguageProvider.get("gui.files_remover.partial_failure_title"), JOptionPane.WARNING_MESSAGE);
                }
            } else {
                for (int i = idxs.size() - 1; i >= 0; i--) {
                    int r = idxs.get(i);
                    Row row = model.rows.get(r);
                    if (row.openAction != null) {
                        executeCustomOpen(row);
                    } else {
                        tryOpen(row.path);
                    }
                }
            }
            layoutColumns();
            updateDisableOpenButtonLabel(btnDisableOrOpen);
        });

        btnRemove.addActionListener(e -> {
            List<Integer> idxs = selectedRowIndices();
            if (idxs.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        LanguageProvider.get("gui.files_remover.select_first_warning_body"),
                        LanguageProvider.get("gui.files_remover.select_first_warning_title"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            List<String> failures = new ArrayList<>();
            for (int i = idxs.size() - 1; i >= 0; i--) {
                int r = idxs.get(i);
                Row row = model.rows.get(r);
                boolean deleted = row.deleteAction != null ? executeCustomDelete(row) : tryDelete(row.path);
                if (deleted) model.removeRow(r);
                else failures.add(row.displayName);
            }
            if (!failures.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        String.format(LanguageProvider.get("gui.files_remover.partial_remove_failures"), String.join("\n", failures)),
                        LanguageProvider.get("gui.files_remover.partial_failure_title"), JOptionPane.WARNING_MESSAGE);
            }
            layoutColumns();
            updateDisableOpenButtonLabel(btnDisableOrOpen);
        });

        btnClose.addActionListener(e -> dispose());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottom.add(btnDisableOrOpen);
        bottom.add(btnRemove);
        bottom.add(btnClose);

        // Layout
        this.scroll = new JScrollPane(table);
        JPanel root = new JPanel(new BorderLayout());
        root.add(desc, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        // Layout columns initially and on resize/show
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                layoutColumns();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(FilesRemover.this::layoutColumns);
            }
        });

        // Initialize label for JAR mode based on current selection
        updateDisableOpenButtonLabel(btnDisableOrOpen);

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Keep master header checkbox state in sync.
     */
    private void updateHeaderCheck() {
        JTableHeader header = table.getTableHeader();
        headerSelectAll.setSelected(model.allSelected());
        header.repaint();  // triggers header renderer with proper border
    }

    /**
     * Selected rows (model indexes).
     */
    private List<Integer> selectedRowIndices() {
        List<Integer> res = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++)
            if (Boolean.TRUE.equals(model.getValueAt(i, 0))) res.add(i);
        return res;
    }

    /**
     * Return true if all selected rows (JAR mode) are currently disabled.
     */
    private boolean allSelectedDisabled(List<Integer> selectedIdxs) {
        if (mode != Mode.JAR || selectedIdxs.isEmpty()) return false;
        for (int r : selectedIdxs) {
            if (!model.rows.get(r).isDisabledJar()) return false;
        }
        return true;
    }

    /**
     * Set the Disable/Open button label depending on the current selection.
     */
    private void updateDisableOpenButtonLabel(JButton btn) {
        if (mode != Mode.JAR) {
            btn.setText(LanguageProvider.get("gui.files_remover.open_selected"));
            return;
        }
        List<Integer> idxs = selectedRowIndices();
        if (!idxs.isEmpty() && allSelectedDisabled(idxs)) {
            btn.setText(LanguageProvider.get("gui.files_remover.enable_selected"));
        } else {
            btn.setText(LanguageProvider.get("gui.files_remover.disable_selected"));
        }
    }

    /**
     * Adaptive column sizing. Non-file columns are sized to content; leftover goes to File column.
     */
    private void layoutColumns() {
        TableColumnModel cm = table.getColumnModel();
        JTableHeader header = table.getTableHeader();
        int colCount = cm.getColumnCount();

        int[] minWidths = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            if (c == 1) continue; // File column handled later
            int headerW = getRendererPrefWidth(header.getDefaultRenderer(), header, cm.getColumn(c).getHeaderValue());
            int contentW;
            if (c == 0) {
                TableCellRenderer r = table.getDefaultRenderer(Boolean.class);
                contentW = getMaxCellPrefWidth(r, c);
            } else {
                ButtonRenderer r = new ButtonRenderer();
                contentW = getMaxCellPrefWidth(r, c);
            }
            minWidths[c] = Math.max(headerW, contentW) + 12;
        }

        int fixedSum = 0;
        for (int c = 0; c < colCount; c++) if (c != 1) fixedSum += Math.max(24, minWidths[c]);

        int viewportW = scroll.getViewport().getWidth();
        if (viewportW <= 0) viewportW = table.getPreferredSize().width;

        for (int c = 0; c < colCount; c++) {
            if (c == 1) continue;
            TableColumn tc = cm.getColumn(c);
            int w = Math.max(24, minWidths[c]);
            tc.setMinWidth(w);
            tc.setPreferredWidth(w);
            tc.setMaxWidth(w);
        }

        int leftover = Math.max(80, viewportW - fixedSum - 4);
        TableColumn fileCol = cm.getColumn(1);
        fileCol.setMinWidth(60);
        fileCol.setPreferredWidth(leftover);
        fileCol.setMaxWidth(Integer.MAX_VALUE);
        fileCol.setCellRenderer(new FileNameRenderer());

        table.revalidate();
        table.repaint();
    }

    private int getRendererPrefWidth(TableCellRenderer r, JComponent parent, Object value) {
        Component comp = r.getTableCellRendererComponent(table, value, false, false, -1, -1);
        comp.doLayout();
        return comp.getPreferredSize().width;
    }

    private int getMaxCellPrefWidth(TableCellRenderer r, int col) {
        int max = 0;
        int rows = Math.max(1, model.getRowCount());
        for (int i = 0; i < rows; i++) {
            Object v;
            if (i < model.getRowCount()) {
                v = model.getValueAt(i, col);
            } else {
                // Provide appropriate default values for each column when table is empty
                switch (col) {
                    case 0:
                        v = Boolean.FALSE;
                        break;  // checkbox column
                    case 1:
                        v = "";
                        break;             // file name column
                    default:
                        v = "";
                        break;            // button columns
                }
            }
            Component comp = r.getTableCellRendererComponent(table, v, false, false, i, col);
            comp.doLayout();
            max = Math.max(max, comp.getPreferredSize().width);
        }
        return max + 6;
    }

    // ------------------------------- DEMO LAUNCHER -------------------------------

    public static void main(String[] args) {
        ThemeUtils.ensureThemesApplied();
        SwingUtilities.invokeLater(() -> {
            JFrame launcher = new JFrame("FilesRemover Demo");
            launcher.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JLabel tip = new JLabel(
                    "<html><body style='padding:6px'>Choose what to open:<br>" +
                            "• <b>Mods</b>: scans relative <code>./mods</code> (non-recursive).<br>" +
                            "• <b>Configs</b>: scans relative <code>./config</code> (recursive).</body></html>"
            );

            JButton openMods = new JButton("Open Mods (JAR mode)");
            JButton openConfigs = new JButton("Open Configs (CONFIG mode)");

            openMods.addActionListener(e -> {
                Path mods = ModListUtils.MODS_FOLDER;
                List<Path> files = listFilesNonRecursive(mods);
                if (files.isEmpty()) {
                    JOptionPane.showMessageDialog(launcher,
                            "No files found in ./mods.\nCreate the folder and put some files inside.",
                            "Nothing to show", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                FilesRemover.showDialog(launcher, files, Mode.JAR);
            });

            openConfigs.addActionListener(e -> {
                Path cfg = Paths.get("config");
                List<Path> files = listFilesRecursive(cfg);
                if (files.isEmpty()) {
                    JOptionPane.showMessageDialog(launcher,
                            "No files found under ./config (searched recursively).\nCreate the folder and put some files inside.",
                            "Nothing to show", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                // Show relative paths as display names (Map API).
                Map<String, Path> map = new LinkedHashMap<>();
                for (Path p : files) {
                    Path rel = cfg.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize());
                    map.put(rel.toString(), p);
                }
                FilesRemover.showDialog(launcher, map, Mode.CONFIG);
            });

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
            buttons.add(openMods);
            buttons.add(openConfigs);

            JPanel root = new JPanel(new BorderLayout());
            tip.setBorder(new EmptyBorder(8, 10, 0, 10));
            root.add(tip, BorderLayout.NORTH);
            root.add(buttons, BorderLayout.CENTER);

            launcher.setContentPane(root);
            launcher.setSize(560, 160);
            launcher.setLocationByPlatform(true);
            launcher.setVisible(true);
        });
    }

    // Demo helpers: list files from ./mods (non-recursive) and ./config (recursive)
    private static List<Path> listFilesNonRecursive(Path dir) {
        try {
            if (!Files.isDirectory(dir)) return Collections.emptyList();
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<Path> listFilesRecursive(Path dir) {
        try {
            if (!Files.isDirectory(dir)) return Collections.emptyList();
            try (Stream<Path> s = Files.walk(dir)) {
                return s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
