package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.app.gui.ControlPanel;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SectionPanel {
    private final ModListDiffDialog dialog;
    private final ModListDiffDialog.SectionType type;
    private final List<DiffEntry> entries;
    private final JPanel container;
    private final JTable table;
    private final JCheckBox master = new JCheckBox();
    private final JLabel headerLabel = new JLabel();
    private boolean collapsed = false;
    private final Model model;
    private final String fileColumnLabel;
    private final boolean editableComparison;
    private final Map<Integer, ModListDiffDialog.SectionAction> actionColumns = new HashMap<Integer, ModListDiffDialog.SectionAction>();
    private JComponent body;
    private JPanel headerPanel;

    SectionPanel(ModListDiffDialog dialog, ModListDiffDialog.SectionType type, List<DiffEntry> entries) {
        this.dialog = dialog;
        this.type = type;
        this.entries = entries;
        this.editableComparison = dialog.isCurrentInstallationEditable();
        this.fileColumnLabel = LanguageProvider.get("gui.modlist_diff.column.file");
        this.model = new Model();
        this.container = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = new Dimension();
                if (headerPanel != null) {
                    Dimension h = headerPanel.getPreferredSize();
                    d.width = Math.max(d.width, h.width);
                    d.height += h.height;
                }
                if (body != null && body.isVisible()) {
                    Dimension b = body.getPreferredSize();
                    d.width = Math.max(d.width, b.width);
                    d.height += b.height;
                }
                Insets in = getInsets();
                d.width += in.left + in.right;
                d.height += in.top + in.bottom;
                return d;
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                Dimension d = getPreferredSize();
                return new Dimension(d.width, d.height);
            }
        };
        this.table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                DiffEntry entry = entries.get(convertRowIndexToModel(row));
                int modelCol = convertColumnIndexToModel(column);
                boolean active = dialog.isEntryActive(entry);
                boolean enabled = active
                        && (modelCol != model.selectionColumn || entry.actionableModFileEntry);
                if (c instanceof JButton) {
                    enabled = active && c.isEnabled();
                    ModListDiffDialog.SectionAction action = actionColumns.get(modelCol);
                    if (action != null) {
                        enabled = dialog.isActionEnabled(entry, action);
                    }
                }
                c.setEnabled(enabled);
                if (c instanceof JComponent) {
                    Color base = isRowSelected(row) ? getSelectionBackground() : getBackground();
                    ((JComponent) c).setOpaque(true);
                    ((JComponent) c).setBackground(!active || entry.resolved ? new Color(235, 235, 235) : base);
                }
                c.setForeground(!active || entry.resolved ? Color.GRAY : getForeground());
                return c;
            }

            @Override
            public String getToolTipText(MouseEvent event) {
                int rowIndex = rowAtPoint(event.getPoint());
                int colIndex = columnAtPoint(event.getPoint());
                int modelColumn = colIndex < 0 ? -1 : convertColumnIndexToModel(colIndex);
                if (rowIndex >= 0 && modelColumn == model.fileColumn) {
                    DiffEntry entry = entries.get(convertRowIndexToModel(rowIndex));
                    return entry.tooltip;
                }
                return super.getToolTipText(event);
            }
        };

        table.putClientProperty("sectionPanel", this);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        if (editableComparison) {
            table.setDefaultEditor(Boolean.class, new DefaultCellEditor(new JCheckBox()));
            table.setDefaultRenderer(Boolean.class, table.getDefaultRenderer(Boolean.class));
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ControlPanel.stopMovingToTop = true;
            }
        });
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setToolTipText(dialog.getComparisonDescription());

        configureColumns();
        model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                updateHeader();
                dialog.updateStatusLabel();
                container.revalidate();
                updateRowHeights();
            }
        });

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        header.setBorder(new EmptyBorder(4, 6, 4, 6));
        master.addActionListener(e -> toggleAll());
        JButton toggle = new JButton("▼");
        toggle.addActionListener(e -> toggle());
        if (editableComparison) {
            header.add(master);
        }
        header.add(toggle);
        header.add(headerLabel);
        headerPanel = header;
        container.add(header, BorderLayout.NORTH);
        if (entries.isEmpty()) {
            JLabel empty = new JLabel(getEmptyText(type));
            empty.setBorder(new EmptyBorder(8, 12, 8, 12));
            body = empty;
            if (editableComparison) {
                master.setEnabled(false);
            }
        } else {
            JPanel tableContainer = new JPanel(new BorderLayout());
            tableContainer.add(table.getTableHeader(), BorderLayout.NORTH);
            tableContainer.add(table, BorderLayout.CENTER);
            body = tableContainer;
        }
        container.add(body, BorderLayout.CENTER);
        updateHeader();
    }

    JComponent getComponent() {
        return container;
    }

    void refresh() {
        model.fireTableDataChanged();
        updateHeader();
        updateRowHeights();
    }

    void toggle() {
        collapsed = !collapsed;
        body.setVisible(!collapsed);
        Component header = container.getComponent(0);
        if (header instanceof JPanel) {
            Component[] comps = ((JPanel) header).getComponents();
            for (Component c : comps) {
                if (c instanceof JButton) {
                    ((JButton) c).setText(collapsed ? "►" : "▼");
                    break;
                }
            }
        }
        container.revalidate();
        container.repaint();
    }

    private String getEmptyText(ModListDiffDialog.SectionType type) {
        switch (type) {
            case ADDED:
                return LanguageProvider.get("gui.modlist_diff.section.empty.added");
            case UPDATED:
                return LanguageProvider.get("gui.modlist_diff.section.empty.updated");
            default:
                return LanguageProvider.get("gui.modlist_diff.section.empty.removed");
        }
    }

    private void toggleAll() {
        if (!editableComparison || !dialog.isDialogOpen()) return;
        boolean target = master.isSelected();
        for (DiffEntry entry : entries) {
            if (!entry.resolved && entry.actionableModFileEntry) {
                entry.selected = target;
            }
        }
        model.fireTableDataChanged();
    }

    private void configureColumns() {
        TableColumnModel cm = table.getColumnModel();
        TableColumn fileCol = cm.getColumn(model.fileColumn);
        fileCol.setCellRenderer(new FileNameRenderer());
        int dynamicNameWidth = Math.max(measureWidestEntry(), dialog.measureHeaderWidth(fileColumnLabel));
        if (table.getPreferredScrollableViewportSize().width > 0) {
            dynamicNameWidth = Math.max(dynamicNameWidth, table.getPreferredScrollableViewportSize().width / 2);
        }

        if (model.selectionColumn >= 0) {
            cm.getColumn(model.selectionColumn).setPreferredWidth(40);
            cm.getColumn(model.selectionColumn).setMaxWidth(40);
            cm.getColumn(model.selectionColumn).setCellRenderer(new BooleanRenderer());
            cm.getColumn(model.selectionColumn).setCellEditor(new DefaultCellEditor(new JCheckBox()));
        }
        fileCol.setPreferredWidth(dynamicNameWidth);
        if (type == ModListDiffDialog.SectionType.UPDATED) {
            cm.getColumn(model.previousFileColumn).setPreferredWidth(Math.max(measureUpdatedFromWidth(), 120));
        }
        cm.getColumn(model.curseForgeColumn).setPreferredWidth(64);
        cm.getColumn(model.curseForgeColumn).setMaxWidth(64);
        cm.getColumn(model.modrinthColumn).setPreferredWidth(64);
        cm.getColumn(model.modrinthColumn).setMaxWidth(64);

        TableCellRenderer cfRenderer = new IconButtonRenderer(dialog.getCfIcon(), false);
        TableCellRenderer mrRenderer = new IconButtonRenderer(dialog.getMrIcon(), true);
        cm.getColumn(model.curseForgeColumn).setCellRenderer(cfRenderer);
        cm.getColumn(model.modrinthColumn).setCellRenderer(mrRenderer);
        cm.getColumn(model.curseForgeColumn).setCellEditor(new IconButtonEditor(this, false));
        cm.getColumn(model.modrinthColumn).setCellEditor(new IconButtonEditor(this, true));

        int startActionCol = model.actionStartColumn;
        int colIdx = startActionCol;
        if (editableComparison) {
            switch (type) {
                case ADDED:
                    actionColumns.put(colIdx, ModListDiffDialog.SectionAction.DISABLE);
                    colIdx++;
                    actionColumns.put(colIdx, ModListDiffDialog.SectionAction.REMOVE);
                    colIdx++;
                    actionColumns.put(colIdx, ModListDiffDialog.SectionAction.SHOW_FOLDER);
                    break;
                case UPDATED:
                    cm.getColumn(colIdx).setPreferredWidth(140); // revert action
                    actionColumns.put(colIdx, ModListDiffDialog.SectionAction.REVERT);
                    actionColumns.put(colIdx + 1, ModListDiffDialog.SectionAction.DISABLE);
                    actionColumns.put(colIdx + 2, ModListDiffDialog.SectionAction.REMOVE);
                    actionColumns.put(colIdx + 3, ModListDiffDialog.SectionAction.SHOW_FOLDER);
                    break;
                case REMOVED:
                    actionColumns.put(colIdx, ModListDiffDialog.SectionAction.RESTORE);
                    break;
            }
        }

        for (int i = startActionCol; i < model.getColumnCount(); i++) {
            ModListDiffDialog.SectionAction action = actionColumns.get(i);
            if (action != null) {
                int w = measureActionColumnWidth(action, model.getColumnName(i));
                cm.getColumn(i).setPreferredWidth(w);
                cm.getColumn(i).setMaxWidth(w);
                cm.getColumn(i).setMinWidth(w);
                cm.getColumn(i).setCellRenderer(new ActionButtonRenderer());
                cm.getColumn(i).setCellEditor(new ActionButtonEditor(this, action));
            }
        }
        updateRowHeights();
    }

    private int measureWidestEntry() {
        int max = 0;
        FontMetrics fm = table.getFontMetrics(table.getFont());
        for (DiffEntry entry : entries) {
            max = Math.max(max, measureLines(fm, entry.primaryDisplayLines()));
        }
        return max + 32; // padding and checkbox spacing
    }

    private int measureUpdatedFromWidth() {
        if (type != ModListDiffDialog.SectionType.UPDATED) return 0;
        int max = 0;
        FontMetrics fm = table.getFontMetrics(table.getFont());
        for (DiffEntry entry : entries) {
            max = Math.max(max, measureLines(fm, entry.savedDisplayLines()));
        }
        return max + 24;
    }

    private int measureActionColumnWidth(ModListDiffDialog.SectionAction action, String header) {
        List<String> texts = new ArrayList<String>();
        texts.add(header);

        switch (action) {
            case DISABLE:
                texts.add(dialog.getRowEnableLabel());
                break;
            case REMOVE:
                texts.add(LanguageProvider.get("gui.modlist_diff.section.removed"));
                break;
            case REVERT:
                texts.add(LanguageProvider.get("gui.modlist_diff.actions.reverting"));
                texts.add(LanguageProvider.get("gui.modlist_diff.actions.reverted"));
                break;
            case RESTORE:
                texts.add(LanguageProvider.get("gui.modlist_diff.actions.restoring"));
                texts.add(LanguageProvider.get("gui.modlist_diff.actions.restored"));
                break;
            case SHOW_FOLDER:
                texts.add(LanguageProvider.get("gui.show"));
                break;
        }

        int max = 0;
        JButton dummy = new JButton();
        dummy.setMargin(new Insets(2, 6, 2, 6));
        for (String s : texts) {
            if (s != null) {
                dummy.setText(s);
                max = Math.max(max, dummy.getPreferredSize().width);
            }
        }
        // Add a small safety padding
        return max + 10;
    }

    private int measureLines(FontMetrics fm, List<String> lines) {
        int max = 0;
        for (String line : lines) {
            if (line != null) {
                max = Math.max(max, fm.stringWidth(line));
            }
        }
        return max;
    }

    private void updateRowHeights() {
        int baseHeight = 28;
        for (int row = 0; row < table.getRowCount(); row++) {
            int modelRow = table.convertRowIndexToModel(row);
            if (modelRow < 0 || modelRow >= entries.size()) continue;
            DiffEntry entry = entries.get(modelRow);
            int lines = Math.max(1, entry.maxDisplayLines());
            table.setRowHeight(row, Math.max(baseHeight, baseHeight * lines));
        }
    }

    void updateHeader() {
        int selected = 0;
        int selectable = 0;
        int total = 0;
        for (DiffEntry e : entries) {
            if (!e.resolved) {
                total++;
                if (e.actionableModFileEntry) {
                    selectable++;
                    if (e.selected) selected++;
                }
            }
        }
        if (editableComparison) {
            master.setEnabled(selectable > 0);
            master.setSelected(selected == selectable && selectable > 0);
        }
        String name;
        switch (type) {
            case ADDED:
                name = LanguageProvider.get("gui.modlist_diff.section.added");
                break;
            case UPDATED:
                name = LanguageProvider.get("gui.modlist_diff.section.updated");
                break;
            default:
                name = LanguageProvider.get("gui.modlist_diff.section.removed");
        }
        headerLabel.setText(name + " (" + total + ")");
    }

    private abstract class BaseButtonEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        protected final JButton button = new JButton();
        protected final SectionPanel panel;
        protected int row;

        BaseButtonEditor(SectionPanel panel) {
            this.panel = panel;
            button.addActionListener(this);
            button.setMargin(new Insets(2, 6, 2, 6));
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            this.row = table.convertRowIndexToModel(row);
            button.setText(value == null ? "" : String.valueOf(value));
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return button.getText();
        }
    }

    private class ActionButtonEditor extends BaseButtonEditor {
        private final ModListDiffDialog.SectionAction action;

        ActionButtonEditor(SectionPanel panel, ModListDiffDialog.SectionAction action) {
            super(panel);
            this.action = action;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            DiffEntry entry = panel.entries.get(this.row);
            button.setEnabled(dialog.isActionEnabled(entry, action));
            return c;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!dialog.isDialogOpen()) {
                fireEditingCanceled();
                return;
            }
            DiffEntry entry = panel.entries.get(row);
            ModListDiffDialog.SectionAction effective = action;
            if (action == ModListDiffDialog.SectionAction.DISABLE && dialog.isDisabledEntry(entry)) {
                effective = ModListDiffDialog.SectionAction.ENABLE;
            }
            if (!dialog.isActionEnabled(entry, effective)) {
                fireEditingCanceled();
                return;
            }
            dialog.runActionAsync(entry, effective);
            fireEditingStopped();
        }
    }

    private class IconButtonEditor extends BaseButtonEditor {
        private final boolean modrinth;

        IconButtonEditor(SectionPanel panel, boolean modrinth) {
            super(panel);
            this.modrinth = modrinth;
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
            button.setIcon(modrinth ? dialog.getMrIcon() : dialog.getCfIcon());
            return c;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!dialog.isDialogOpen()) {
                fireEditingCanceled();
                return;
            }
            DiffEntry entry = panel.entries.get(row);
            if (modrinth) {
                dialog.openModrinthProject(entry);
            } else {
                dialog.openCurseForgeProject(entry);
            }
            fireEditingStopped();
        }
    }

    private class ActionButtonRenderer extends JButton implements TableCellRenderer {
        ActionButtonRenderer() {
            setOpaque(true);
            setMargin(new Insets(2, 6, 2, 6));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : String.valueOf(value));
            SectionPanel panel = (SectionPanel) table.getClientProperty("sectionPanel");
            if (panel != null && table.getRowCount() > 0) {
                DiffEntry entry = panel.entries.get(table.convertRowIndexToModel(row));
                int modelColumn = table.convertColumnIndexToModel(column);
                ModListDiffDialog.SectionAction action = panel.actionColumns.get(modelColumn);
                setEnabled(dialog.isActionEnabled(entry, action));
            } else {
                setEnabled(false);
            }
            return this;
        }
    }

    private class IconButtonRenderer extends JButton implements TableCellRenderer {
        private final ImageIcon icon;
        private final boolean modrinth;

        IconButtonRenderer(ImageIcon icon, boolean modrinth) {
            this.icon = icon;
            this.modrinth = modrinth;
            setOpaque(true);
            setBorderPainted(false);
            setContentAreaFilled(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            SectionPanel panel = (SectionPanel) table.getClientProperty("sectionPanel");
            setIcon(null);
            if (panel != null && table.getRowCount() > 0) {
                DiffEntry entry = panel.entries.get(table.convertRowIndexToModel(row));
                boolean hasPlatform = modrinth
                        ? entry.hasAnyModrinthMatch()
                        : entry.hasAnyCurseMatch();
                boolean lookupReady = modrinth ? dialog.isMrReady() : dialog.isCfReady();
                if (!lookupReady) {
                    setEnabled(false);
                    setToolTipText(LanguageProvider.get("gui.modlist_diff.fetching_warning"));
                } else {
                    setToolTipText(null);
                    setEnabled(hasPlatform && icon != null && dialog.isEntryActive(entry));
                }
                if (hasPlatform) {
                    setIcon(icon);
                }
            } else {
                setEnabled(false);
            }
            return this;
        }
    }

    private static class FileNameRenderer extends DefaultTableCellRenderer {
        private final Color disabledColor = new Color(255, 0, 0);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            SectionPanel panel = (SectionPanel) table.getClientProperty("sectionPanel");
            if (panel != null) {
                DiffEntry entry = panel.entries.get(table.convertRowIndexToModel(row));
                boolean disabled = entry.areAllCurrentDisabled();
                if (disabled && value instanceof String) {
                    setText(wrapRed((String) value));
                } else {
                    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                }
            }
            return c;
        }

        private String wrapRed(String text) {
            if (text == null) return "";
            if (text.startsWith("<html>")) {
                return text.replaceFirst("<html>", "<html><span style='color:#ff0000;'>")
                        .replaceFirst("</html>$", "</span></html>");
            }
            return "<html><span style='color:#ff0000;'>" + text + "</span></html>";
        }
    }

    private static class BooleanRenderer extends JCheckBox implements TableCellRenderer {
        BooleanRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setSelected(value instanceof Boolean && (Boolean) value);
            setEnabled(table.isEnabled());
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }
    }

    private class Model extends AbstractTableModel {
        private final String[] columns;
        private final int selectionColumn;
        private final int fileColumn;
        private final int previousFileColumn;
        private final int curseForgeColumn;
        private final int modrinthColumn;
        private final int actionStartColumn;

        Model() {
            List<String> cols = new ArrayList<String>();
            int nextColumn = 0;
            if (editableComparison) {
                selectionColumn = nextColumn++;
                cols.add("");
            } else {
                selectionColumn = -1;
            }
            fileColumn = nextColumn++;
            cols.add(fileColumnLabel);
            boolean updated = type == ModListDiffDialog.SectionType.UPDATED;
            if (updated) {
                previousFileColumn = nextColumn++;
                cols.add(LanguageProvider.get("gui.modlist_diff.updated_from"));
            } else {
                previousFileColumn = -1;
            }
            curseForgeColumn = nextColumn++;
            cols.add("CF");
            modrinthColumn = nextColumn++;
            cols.add("MR");
            actionStartColumn = nextColumn;
            if (editableComparison) {
                switch (type) {
                    case ADDED:
                        cols.add(LanguageProvider.get("gui.files_remover.disable"));
                        cols.add(LanguageProvider.get("gui.files_remover.remove"));
                        cols.add(LanguageProvider.get("gui.show_in_explorer_button"));
                        break;
                    case UPDATED:
                        cols.add(LanguageProvider.get("gui.modlist_diff.actions.revert"));
                        cols.add(LanguageProvider.get("gui.files_remover.disable"));
                        cols.add(LanguageProvider.get("gui.files_remover.remove"));
                        cols.add(LanguageProvider.get("gui.show_in_explorer_button"));
                        break;
                    case REMOVED:
                        cols.add(LanguageProvider.get("gui.modlist_diff.actions.restore"));
                        break;
                }
            }
            columns = cols.toArray(new String[0]);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == selectionColumn ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            DiffEntry entry = entries.get(rowIndex);
            if (!dialog.isDialogOpen() || !dialog.isEntryActive(entry)) return false;
            if (columnIndex == selectionColumn) {
                return editableComparison && entry.actionableModFileEntry;
            }
            if (columnIndex == curseForgeColumn) return entry.hasAnyCurseMatch();
            if (columnIndex == modrinthColumn) return entry.hasAnyModrinthMatch();
            ModListDiffDialog.SectionAction action = actionColumns.get(columnIndex);
            if (action != null) {
                return dialog.isActionEnabled(entry, action);
            }
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DiffEntry entry = entries.get(rowIndex);
            if (columnIndex == selectionColumn) {
                return entry.selected && !entry.resolved;
            }
            if (columnIndex == fileColumn) return entry.primaryDisplayText();
            if (columnIndex == previousFileColumn) return entry.savedDisplayText();
            if (columnIndex == curseForgeColumn || columnIndex == modrinthColumn) return "";
            ModListDiffDialog.SectionAction action = actionColumns.get(columnIndex);
            if (action != null) {
                return dialog.getActionLabel(entry, action, columns[columnIndex]);
            }
            return "";
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (editableComparison && columnIndex == selectionColumn && dialog.isDialogOpen()
                    && entries.get(rowIndex).actionableModFileEntry) {
                entries.get(rowIndex).selected = (Boolean) aValue;
                fireTableRowsUpdated(rowIndex, rowIndex);
            }
        }
    }
}
