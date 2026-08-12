package dev.kostromdan.mods.crash_assistant.app.gui.modlist.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistorySummary;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStatus;

import java.util.Collection;
import java.util.LinkedHashSet;

final class ModListHistoryRow {
    private final long timestamp;
    private final ModListHistoryStatus status;
    private final LinkedHashSet<Mod> currentMods;
    private final int modsCount;
    private final boolean current;
    private final boolean legacy;
    private final String stableId;

    private ModListHistoryRow(long timestamp,
                              ModListHistoryStatus status,
                              Collection<Mod> currentMods,
                              int modsCount,
                              boolean current,
                              boolean legacy,
                              String stableId) {
        this.timestamp = timestamp;
        this.status = status;
        this.currentMods = currentMods == null ? null : new LinkedHashSet<Mod>(currentMods);
        this.modsCount = modsCount;
        this.current = current;
        this.legacy = legacy;
        this.stableId = stableId;
    }

    static ModListHistoryRow history(ModListHistorySummary summary) {
        String id = summary.isLegacySnapshot()
                ? "legacy:" + summary.getTimestamp() + ":" + String.valueOf(summary.getLegacySource())
                : "history:" + summary.getTimestamp();
        return new ModListHistoryRow(
                summary.getTimestamp(),
                summary.getStatus(),
                null,
                summary.getModsCount(),
                false,
                summary.isLegacySnapshot(),
                id);
    }

    static ModListHistoryRow current(long timestamp,
                                     ModListHistoryStatus status,
                                     Collection<Mod> mods) {
        return new ModListHistoryRow(
                timestamp,
                status == null ? ModListHistoryStatus.STARTED : status,
                mods,
                mods == null ? 0 : mods.size(),
                true,
                false,
                "current");
    }

    long getTimestamp() {
        return timestamp;
    }

    ModListHistoryStatus getStatus() {
        return status;
    }

    int getModsCount() {
        return modsCount;
    }

    LinkedHashSet<Mod> getCurrentMods() {
        if (!current || currentMods == null) {
            throw new IllegalStateException("Only the Current row has an in-memory mod graph");
        }
        return new LinkedHashSet<Mod>(currentMods);
    }

    boolean isCurrent() {
        return current;
    }

    boolean isLegacy() {
        return legacy;
    }

    String getStableId() {
        return stableId;
    }

    int getStatusSortOrder() {
        if (legacy) {
            return -1;
        }
        return status == null ? -1 : status.isClosedWithoutCrash()
                ? ModListHistoryStatus.CLOSED_WITHOUT_CRASH.ordinal()
                : status.ordinal();
    }
}
