package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

/**
 * Persisted state of one Minecraft launch.
 *
 * <p>The first three values are also the live launch milestones. A launch that
 * opens Crash Assistant after joining a world is changed to
 * {@link #CRASHED_DURING_GAMEPLAY}; a launch that exits without opening Crash
 * Assistant is changed to one of the closed states. Those states keep the
 * furthest reached milestone in this single persisted field.</p>
 */
public enum ModListHistoryStatus {
    STARTED,
    TITLE_SCREEN,
    JOINED,
    CRASHED_DURING_GAMEPLAY,
    CLOSED_WITHOUT_CRASH,
    CLOSED_WITHOUT_CRASH_AFTER_TITLE_SCREEN,
    CLOSED_WITHOUT_CRASH_AFTER_JOIN;

    public boolean isClosedWithoutCrash() {
        return this == CLOSED_WITHOUT_CRASH
                || this == CLOSED_WITHOUT_CRASH_AFTER_TITLE_SCREEN
                || this == CLOSED_WITHOUT_CRASH_AFTER_JOIN;
    }

    public boolean reachedTitleScreen() {
        return this == TITLE_SCREEN
                || this == CLOSED_WITHOUT_CRASH_AFTER_TITLE_SCREEN
                || this == CLOSED_WITHOUT_CRASH_AFTER_JOIN;
    }

    public boolean reachedJoinedWorld() {
        return this == JOINED
                || this == CRASHED_DURING_GAMEPLAY
                || this == CLOSED_WITHOUT_CRASH_AFTER_JOIN;
    }
}
