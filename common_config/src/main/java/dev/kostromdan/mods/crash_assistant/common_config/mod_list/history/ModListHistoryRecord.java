package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;

/** Immutable snapshot of the mod list associated with a launch or migration. */
public final class ModListHistoryRecord {
    private final long timestamp;
    private final ModListHistoryStatus status;
    private final LinkedHashSet<Mod> mods;
    private final boolean legacySnapshot;
    private final String legacySource;
    private final String legacyFingerprint;

    public ModListHistoryRecord(long timestamp,
                                ModListHistoryStatus status,
                                Collection<Mod> mods) {
        this(timestamp, status, mods, false, null, null);
    }

    private ModListHistoryRecord(long timestamp,
                                 ModListHistoryStatus status,
                                 Collection<Mod> mods,
                                 boolean legacySnapshot,
                                 String legacySource,
                                 String legacyFingerprint) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be negative");
        }
        if (!legacySnapshot && status == null) {
            throw new IllegalArgumentException("A launch record must have a status");
        }
        if (legacySnapshot && status != null) {
            throw new IllegalArgumentException("A legacy snapshot must not claim a launch status");
        }
        this.timestamp = timestamp;
        this.status = status;
        this.mods = mods == null ? new LinkedHashSet<Mod>() : new LinkedHashSet<>(mods);
        this.legacySnapshot = legacySnapshot;
        this.legacySource = legacySource;
        this.legacyFingerprint = legacyFingerprint;
    }

    public static ModListHistoryRecord started(long timestamp, Collection<Mod> mods) {
        return new ModListHistoryRecord(timestamp, ModListHistoryStatus.STARTED, mods);
    }

    public static ModListHistoryRecord legacy(long timestamp, Collection<Mod> mods, String source) {
        return legacy(timestamp, mods, source, null);
    }

    public static ModListHistoryRecord legacy(long timestamp,
                                              Collection<Mod> mods,
                                              String source,
                                              String fingerprint) {
        return new ModListHistoryRecord(timestamp, null, mods, true, source, fingerprint);
    }

    public long getTimestamp() {
        return timestamp;
    }

    /** Alias retained for callers that deal only with actual launch records. */
    public long getLaunchStartedAt() {
        return timestamp;
    }

    /** Returns {@code null} only for a pre-history migrated snapshot. */
    public ModListHistoryStatus getStatus() {
        return status;
    }

    public Optional<ModListHistoryStatus> getStatusOptional() {
        return Optional.ofNullable(status);
    }

    public LinkedHashSet<Mod> getMods() {
        return new LinkedHashSet<>(mods);
    }

    public int getModsCount() {
        return mods.size();
    }

    public boolean isLegacySnapshot() {
        return legacySnapshot;
    }

    public String getLegacySource() {
        return legacySource;
    }

    public String getLegacyFingerprint() {
        return legacyFingerprint;
    }
}
