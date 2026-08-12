package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

/** Lightweight history metadata; intentionally contains no mod graph. */
public final class ModListHistorySummary {
    private final long timestamp;
    private final ModListHistoryStatus status;
    private final int modsCount;
    private final boolean legacySnapshot;
    private final String legacySource;

    public ModListHistorySummary(long timestamp, ModListHistoryStatus status, int modsCount,
                                 boolean legacySnapshot, String legacySource) {
        this.timestamp = timestamp;
        this.status = status;
        this.modsCount = modsCount;
        this.legacySnapshot = legacySnapshot;
        this.legacySource = legacySource;
    }

    public long getTimestamp() { return timestamp; }
    public ModListHistoryStatus getStatus() { return status; }
    public int getModsCount() { return modsCount; }
    public boolean isLegacySnapshot() { return legacySnapshot; }
    public String getLegacySource() { return legacySource; }
}
