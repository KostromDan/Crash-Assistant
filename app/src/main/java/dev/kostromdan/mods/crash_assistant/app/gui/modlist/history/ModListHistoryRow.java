package dev.kostromdan.mods.crash_assistant.app.gui.modlist.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryRecord;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryStatus;

import java.util.Collection;
import java.util.LinkedHashSet;

final class ModListHistoryRow {
    private final long timestamp;
    private final ModListHistoryStatus status;
    private final LinkedHashSet<Mod> mods;
    private final boolean current;
    private final boolean legacy;
    private final String stableId;

    private ModListHistoryRow(long timestamp,
                              ModListHistoryStatus status,
                              Collection<Mod> mods,
                              boolean current,
                              boolean legacy,
                              String stableId) {
        this.timestamp = timestamp;
        this.status = status;
        this.mods = mods == null ? new LinkedHashSet<Mod>() : new LinkedHashSet<Mod>(mods);
        this.current = current;
        this.legacy = legacy;
        this.stableId = stableId;
    }

    static ModListHistoryRow history(ModListHistoryRecord record) {
        String id = record.isLegacySnapshot()
                ? "legacy:" + record.getTimestamp() + ":" + String.valueOf(record.getLegacySource())
                : "history:" + record.getTimestamp();
        return new ModListHistoryRow(
                record.getTimestamp(),
                record.getStatus(),
                record.getMods(),
                false,
                record.isLegacySnapshot(),
                id);
    }

    static ModListHistoryRow current(long timestamp,
                                     ModListHistoryStatus status,
                                     Collection<Mod> mods) {
        return new ModListHistoryRow(
                timestamp,
                status == null ? ModListHistoryStatus.STARTED : status,
                mods,
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

    LinkedHashSet<Mod> getMods() {
        return new LinkedHashSet<Mod>(mods);
    }

    int getModsCount() {
        return mods.size();
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
        return status == null ? -1 : status.ordinal();
    }
}
