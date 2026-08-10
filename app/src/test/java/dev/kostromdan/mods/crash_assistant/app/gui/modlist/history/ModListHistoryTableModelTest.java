package dev.kostromdan.mods.crash_assistant.app.gui.modlist.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryRecord;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ModListHistoryTableModelTest {
    private ModListHistoryTableModelTest() {
    }

    public static void main(String[] args) {
        ModListHistoryRow current = ModListHistoryRow.current(
                300L,
                ModListHistoryStatus.TITLE_SCREEN,
                Arrays.asList(new Mod("a.jar"), new Mod("b.jar")));
        ModListHistoryRow joined = ModListHistoryRow.history(new ModListHistoryRecord(
                200L,
                ModListHistoryStatus.JOINED,
                true,
                true,
                Collections.singletonList(new Mod("a.jar"))));
        ModListHistoryRow legacy = ModListHistoryRow.history(ModListHistoryRecord.legacy(
                100L,
                Collections.singletonList(new Mod("legacy.jar")),
                "config/crash_assistant/modlist.json"));

        List<ModListHistoryRow> rows = Arrays.asList(current, legacy, joined);
        ModListHistoryTableModel model = new ModListHistoryTableModel(rows);

        assertEquals(3, model.getRowCount(), "row count");
        assertEquals(3, model.getColumnCount(), "column count");
        assertEquals(Integer.class, model.getColumnClass(ModListHistoryTableModel.MODS_COUNT_COLUMN),
                "mod count type");
        assertEquals(ModListHistoryRow.class, model.getColumnClass(ModListHistoryTableModel.DATE_COLUMN),
                "date sort type");
        assertEquals(Integer.valueOf(2), model.getValueAt(0, ModListHistoryTableModel.MODS_COUNT_COLUMN),
                "current mod count");
        assertTrue(model.getRow(0).isCurrent(), "synthetic Current row");
        assertTrue(model.getRow(1).isLegacy(), "legacy snapshot stays unclassified");
        assertEquals(null, model.getRow(1).getStatus(), "legacy snapshot status");
        assertEquals(ModListHistoryStatus.JOINED, model.getRow(2).getStatus(), "joined status");
        assertTrue(!model.getRow(0).getStableId().equals(model.getRow(2).getStableId()),
                "different rows remain independently selectable");

        System.out.println("Mod-list history table model tests passed.");
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
