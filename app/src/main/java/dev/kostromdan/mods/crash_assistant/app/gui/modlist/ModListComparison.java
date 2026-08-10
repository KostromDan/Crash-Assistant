package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * A fully resolved pair of mod-list snapshots to display in the diff dialog.
 *
 * <p>The dialog deliberately does not resolve either side on its own. This is
 * important for history comparisons: reopening the dialog must compare the
 * records selected by the user rather than the global modpack manifest.</p>
 */
public final class ModListComparison {
    public enum SourceKind {
        MODPACK_BASELINE,
        HISTORY,
        LEGACY_SNAPSHOT,
        CURRENT,
        IMPORTED_MODLIST
    }

    private final String title;
    private final String leftLabel;
    private final String rightLabel;
    private final SourceKind leftKind;
    private final SourceKind rightKind;
    private final LinkedHashSet<Mod> leftMods;
    private final LinkedHashSet<Mod> rightMods;
    private final boolean currentInstallationEditable;

    public ModListComparison(String title,
                             String leftLabel,
                             SourceKind leftKind,
                             Collection<Mod> leftMods,
                             String rightLabel,
                             SourceKind rightKind,
                             Collection<Mod> rightMods,
                             boolean currentInstallationEditable) {
        this.title = title == null ? "" : title;
        this.leftLabel = leftLabel == null ? "" : leftLabel;
        this.rightLabel = rightLabel == null ? "" : rightLabel;
        this.leftKind = leftKind;
        this.rightKind = rightKind;
        this.leftMods = copy(leftMods);
        this.rightMods = copy(rightMods);
        this.currentInstallationEditable = currentInstallationEditable
                && rightKind == SourceKind.CURRENT;
    }

    public static ModListComparison againstCurrent(String title,
                                                   String referenceLabel,
                                                   SourceKind referenceKind,
                                                   Collection<Mod> referenceMods,
                                                   String currentLabel,
                                                   Collection<Mod> currentMods) {
        return new ModListComparison(
                title,
                referenceLabel,
                referenceKind,
                referenceMods,
                currentLabel,
                SourceKind.CURRENT,
                currentMods,
                true
        );
    }

    public static ModListComparison readOnly(String title,
                                             String leftLabel,
                                             SourceKind leftKind,
                                             Collection<Mod> leftMods,
                                             String rightLabel,
                                             SourceKind rightKind,
                                             Collection<Mod> rightMods) {
        return new ModListComparison(
                title,
                leftLabel,
                leftKind,
                leftMods,
                rightLabel,
                rightKind,
                rightMods,
                false
        );
    }

    public String getTitle() {
        return title;
    }

    public String getLeftLabel() {
        return leftLabel;
    }

    public String getRightLabel() {
        return rightLabel;
    }

    public SourceKind getLeftKind() {
        return leftKind;
    }

    public SourceKind getRightKind() {
        return rightKind;
    }

    public LinkedHashSet<Mod> getLeftMods() {
        return new LinkedHashSet<Mod>(leftMods);
    }

    public LinkedHashSet<Mod> getRightMods() {
        return new LinkedHashSet<Mod>(rightMods);
    }

    public boolean isCurrentInstallationEditable() {
        return currentInstallationEditable;
    }

    public boolean isModpackBaselineComparison() {
        return leftKind == SourceKind.MODPACK_BASELINE && rightKind == SourceKind.CURRENT;
    }

    public ModListDiff createDiff() {
        return new ModListDiff(getLeftMods(), getRightMods());
    }

    private static LinkedHashSet<Mod> copy(Collection<Mod> mods) {
        return mods == null ? new LinkedHashSet<Mod>() : new LinkedHashSet<Mod>(mods);
    }
}
