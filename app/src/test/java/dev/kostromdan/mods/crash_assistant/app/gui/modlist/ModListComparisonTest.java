package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

public final class ModListComparisonTest {
    private ModListComparisonTest() {
    }

    public static void main(String[] args) {
        LinkedHashSet<Mod> previous = new LinkedHashSet<Mod>();
        previous.add(mod("example-old.jar", "example", "1.0"));
        LinkedHashSet<Mod> current = new LinkedHashSet<Mod>();
        current.add(mod("example-new.jar", "example", "2.0"));

        ModListComparison editable = ModListComparison.againstCurrent(
                "History to current",
                "Previous",
                ModListComparison.SourceKind.HISTORY,
                previous,
                "Current",
                current);

        previous.clear();
        current.clear();
        assertTrue(editable.isCurrentInstallationEditable(), "current comparison is editable");
        assertEquals(1, editable.getLeftMods().size(), "constructor copies left snapshot");
        assertEquals(1, editable.getRightMods().size(), "constructor copies right snapshot");

        editable.getLeftMods().clear();
        ModListDiff diff = editable.createDiff();
        assertEquals(1, diff.getUpdatedMods().size(), "explicit snapshots produce an update");
        assertEquals(0, diff.getAddedMods().size(), "updated mod is not also added");
        assertEquals(0, diff.getRemovedMods().size(), "updated mod is not also removed");

        ModListComparison historyOnly = ModListComparison.readOnly(
                "History comparison",
                "Older",
                ModListComparison.SourceKind.HISTORY,
                editable.getLeftMods(),
                "Newer",
                ModListComparison.SourceKind.HISTORY,
                editable.getRightMods());
        assertTrue(!historyOnly.isCurrentInstallationEditable(), "history-to-history is read-only");

        ModListComparison imported = ModListComparison.readOnly(
                "Imported comparison",
                "Imported",
                ModListComparison.SourceKind.IMPORTED_MODLIST,
                editable.getLeftMods(),
                "Current",
                ModListComparison.SourceKind.CURRENT,
                editable.getRightMods());
        assertTrue(!imported.isCurrentInstallationEditable(), "import-to-current can be explicitly read-only");

        ModListComparison legacy = ModListComparison.readOnly(
                "Previous-version snapshot",
                "Legacy",
                ModListComparison.SourceKind.LEGACY_SNAPSHOT,
                editable.getLeftMods(),
                "Current",
                ModListComparison.SourceKind.CURRENT,
                editable.getRightMods());
        assertTrue(!legacy.isCurrentInstallationEditable(),
                "unknown legacy outcome must never enable destructive actions");

        System.out.println("Mod-list comparison tests passed.");
    }

    private static Mod mod(String jarName, String modId, String version) {
        return new Mod(
                jarName,
                modId,
                modId,
                version,
                false,
                false,
                new HashSet<String>(),
                new ArrayList<Mod>(),
                null,
                null,
                null);
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
