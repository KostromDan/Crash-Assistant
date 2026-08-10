package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;

/** Immutable snapshot of the mod list associated with a launch or migration. */
public final class ModListHistoryRecord {
    private final long timestamp;
    private final ModListHistoryStatus status;
    private final boolean reachedTitleScreen;
    private final boolean reachedJoined;
    private final LinkedHashSet<Mod> mods;
    private final boolean legacySnapshot;
    private final String legacySource;
    private final String legacyFingerprint;

    public ModListHistoryRecord(long timestamp,
                                ModListHistoryStatus status,
                                boolean reachedTitleScreen,
                                boolean reachedJoined,
                                Collection<Mod> mods) {
        this(timestamp, status, reachedTitleScreen, reachedJoined, mods, false, null, null);
    }

    private ModListHistoryRecord(long timestamp,
                                 ModListHistoryStatus status,
                                 boolean reachedTitleScreen,
                                 boolean reachedJoined,
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
        if (reachedJoined && !legacySnapshot && status == ModListHistoryStatus.STARTED) {
            throw new IllegalArgumentException("A STARTED launch cannot have joined a world");
        }
        this.timestamp = timestamp;
        this.status = status;
        this.reachedTitleScreen = reachedTitleScreen;
        this.reachedJoined = reachedJoined;
        this.mods = mods == null ? new LinkedHashSet<Mod>() : new LinkedHashSet<>(mods);
        this.legacySnapshot = legacySnapshot;
        this.legacySource = legacySource;
        this.legacyFingerprint = legacyFingerprint;
    }

    public static ModListHistoryRecord started(long timestamp, Collection<Mod> mods) {
        return new ModListHistoryRecord(timestamp, ModListHistoryStatus.STARTED, false, false, mods);
    }

    public static ModListHistoryRecord legacy(long timestamp, Collection<Mod> mods, String source) {
        return legacy(timestamp, mods, source, null);
    }

    public static ModListHistoryRecord legacy(long timestamp,
                                              Collection<Mod> mods,
                                              String source,
                                              String fingerprint) {
        return new ModListHistoryRecord(timestamp, null, false, false, mods, true, source, fingerprint);
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

    public boolean hasReachedTitleScreen() {
        return reachedTitleScreen;
    }

    public boolean hasReachedJoined() {
        return reachedJoined;
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

    ModListHistoryRecord withMilestone(ModListHistoryStatus milestone,
                                       boolean titleScreenReached,
                                       boolean joinedReached) {
        if (legacySnapshot || status == ModListHistoryStatus.CRASHED_DURING_GAMEPLAY
                || status == ModListHistoryStatus.CLOSED_WITHOUT_CRASH) {
            return this;
        }
        if (milestone != ModListHistoryStatus.TITLE_SCREEN && milestone != ModListHistoryStatus.JOINED) {
            throw new IllegalArgumentException("Not a milestone: " + milestone);
        }
        ModListHistoryStatus resultingStatus = status == ModListHistoryStatus.JOINED
                || milestone == ModListHistoryStatus.JOINED
                ? ModListHistoryStatus.JOINED
                : ModListHistoryStatus.TITLE_SCREEN;
        return new ModListHistoryRecord(
                timestamp,
                resultingStatus,
                reachedTitleScreen || titleScreenReached,
                reachedJoined || joinedReached,
                mods);
    }

    ModListHistoryRecord withCrashAssistantOpened() {
        if (legacySnapshot || status == ModListHistoryStatus.CLOSED_WITHOUT_CRASH) {
            return this;
        }
        if (!reachedJoined && status != ModListHistoryStatus.JOINED) {
            // STARTED and TITLE_SCREEN are intentionally retained: their UI names
            // describe a crash during startup / while joining a world.
            return this;
        }
        return new ModListHistoryRecord(
                timestamp,
                ModListHistoryStatus.CRASHED_DURING_GAMEPLAY,
                reachedTitleScreen,
                true,
                mods);
    }

    ModListHistoryRecord withClosedWithoutCrash() {
        if (legacySnapshot || status == ModListHistoryStatus.CRASHED_DURING_GAMEPLAY) {
            return this;
        }
        return new ModListHistoryRecord(
                timestamp,
                ModListHistoryStatus.CLOSED_WITHOUT_CRASH,
                reachedTitleScreen,
                reachedJoined || status == ModListHistoryStatus.JOINED,
                mods);
    }
}
