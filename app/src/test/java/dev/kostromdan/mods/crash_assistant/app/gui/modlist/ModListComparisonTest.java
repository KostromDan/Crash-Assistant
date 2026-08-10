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
        testExplicitAgainstCurrentComparison();
        testEverySingleCurrentComparisonIsEditable();
        testCurrentOnLeftIsCanonicallySwapped();
        testSnapshotToSnapshotComparisonStaysReadOnly();

        System.out.println("Mod-list comparison tests passed.");
    }

    private static void testExplicitAgainstCurrentComparison() {
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
    }

    private static void testEverySingleCurrentComparisonIsEditable() {
        LinkedHashSet<Mod> reference = mods(mod("reference.jar", "reference", "1.0"));
        LinkedHashSet<Mod> current = mods(mod("current.jar", "current", "1.0"));

        ModListComparison.SourceKind[] referenceKinds = {
                ModListComparison.SourceKind.MODPACK_BASELINE,
                ModListComparison.SourceKind.HISTORY,
                ModListComparison.SourceKind.LEGACY_SNAPSHOT,
                ModListComparison.SourceKind.IMPORTED_MODLIST
        };
        for (ModListComparison.SourceKind referenceKind : referenceKinds) {
            ModListComparison comparison = ModListComparison.readOnly(
                    "Reference to current",
                    "Reference",
                    referenceKind,
                    reference,
                    "Current",
                    ModListComparison.SourceKind.CURRENT,
                    current);

            assertTrue(comparison.isCurrentInstallationEditable(),
                    referenceKind + "-to-current comparison is editable");
            assertEquals(referenceKind, comparison.getLeftKind(),
                    referenceKind + " remains the reference side");
            assertEquals(ModListComparison.SourceKind.CURRENT, comparison.getRightKind(),
                    "Current remains the managed-installation side");
        }
    }

    private static void testCurrentOnLeftIsCanonicallySwapped() {
        LinkedHashSet<Mod> current = mods(mod("example-new.jar", "example", "2.0"));
        LinkedHashSet<Mod> history = mods(mod("example-old.jar", "example", "1.0"));

        ModListComparison comparison = ModListComparison.readOnly(
                "Current selected first",
                "Current",
                ModListComparison.SourceKind.CURRENT,
                current,
                "Previous launch",
                ModListComparison.SourceKind.HISTORY,
                history);

        assertTrue(comparison.isCurrentInstallationEditable(),
                "comparison remains editable when Current was selected on the left");
        assertEquals(ModListComparison.SourceKind.HISTORY, comparison.getLeftKind(),
                "reference is normalized to the left");
        assertEquals(ModListComparison.SourceKind.CURRENT, comparison.getRightKind(),
                "Current is normalized to the managed-installation side");
        assertEquals("Previous launch", comparison.getLeftLabel(), "reference label follows the swap");
        assertEquals("Current", comparison.getRightLabel(), "Current label follows the swap");
        assertEquals("example-old.jar", onlyMod(comparison.getLeftMods()).getJarName(),
                "reference mods follow the swap");
        assertEquals("example-new.jar", onlyMod(comparison.getRightMods()).getJarName(),
                "current mods follow the swap");

        ModListDiff diff = comparison.createDiff();
        assertEquals(1, diff.getUpdatedMods().size(),
                "normalized comparison keeps reference-to-current diff direction");
        assertEquals("example-old.jar",
                onlyMod(diff.getUpdatedMods().iterator().next().getOldMods()).getJarName(),
                "old mod comes from the selected history launch");
        assertEquals("example-new.jar",
                onlyMod(diff.getUpdatedMods().iterator().next().getNewMods()).getJarName(),
                "new mod comes from Current");
    }

    private static void testSnapshotToSnapshotComparisonStaysReadOnly() {
        LinkedHashSet<Mod> left = mods(mod("left.jar", "left", "1.0"));
        LinkedHashSet<Mod> right = mods(mod("right.jar", "right", "1.0"));

        ModListComparison historyOnly = ModListComparison.readOnly(
                "History comparison",
                "Older",
                ModListComparison.SourceKind.HISTORY,
                left,
                "Newer",
                ModListComparison.SourceKind.HISTORY,
                right);
        assertTrue(!historyOnly.isCurrentInstallationEditable(), "history-to-history is read-only");
    }

    private static LinkedHashSet<Mod> mods(Mod mod) {
        LinkedHashSet<Mod> result = new LinkedHashSet<Mod>();
        result.add(mod);
        return result;
    }

    private static Mod onlyMod(LinkedHashSet<Mod> mods) {
        assertEquals(1, mods.size(), "single-mod test fixture");
        return mods.iterator().next();
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

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
