package dev.kostromdan.mods.crash_assistant.app.gui.modlist.history;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class ModListHistoryTableModel extends AbstractTableModel {
    static final int STATUS_COLUMN = 0;
    static final int DATE_COLUMN = 1;
    static final int MODS_COUNT_COLUMN = 2;

    private final List<ModListHistoryRow> rows;

    ModListHistoryTableModel(List<ModListHistoryRow> rows) {
        this.rows = new ArrayList<ModListHistoryRow>(rows);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case STATUS_COLUMN:
                return LanguageProvider.get("gui.modlist_history.column.status");
            case DATE_COLUMN:
                return LanguageProvider.get("gui.modlist_history.column.date_time");
            case MODS_COUNT_COLUMN:
                return LanguageProvider.get("gui.modlist_history.column.mods_count");
            default:
                throw new IllegalArgumentException("Unknown column " + column);
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == MODS_COUNT_COLUMN ? Integer.class : ModListHistoryRow.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ModListHistoryRow row = rows.get(rowIndex);
        return columnIndex == MODS_COUNT_COLUMN ? Integer.valueOf(row.getModsCount()) : row;
    }

    ModListHistoryRow getRow(int modelIndex) {
        return rows.get(modelIndex);
    }
}
