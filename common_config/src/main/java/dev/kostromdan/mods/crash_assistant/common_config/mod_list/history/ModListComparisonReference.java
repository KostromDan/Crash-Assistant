package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

/** A selected history baseline plus the wording the GUI should use for it. */
public final class ModListComparisonReference {
    public enum Kind {
        TITLE_SCREEN,
        JOINED,
        LEGACY_SNAPSHOT
    }

    private final ModListHistoryRecord record;
    private final Kind kind;

    public ModListComparisonReference(ModListHistoryRecord record, Kind kind) {
        if (record == null || kind == null) {
            throw new IllegalArgumentException("record and kind are required");
        }
        this.record = record;
        this.kind = kind;
    }

    public ModListHistoryRecord getRecord() {
        return record;
    }

    public Kind getKind() {
        return kind;
    }
}
