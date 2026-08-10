package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

/**
 * Persisted state of one Minecraft launch.
 *
 * <p>The first three values are also the live launch milestones. A launch that
 * opens Crash Assistant after joining a world is changed to
 * {@link #CRASHED_DURING_GAMEPLAY}; a launch that exits without opening Crash
 * Assistant is changed to {@link #CLOSED_WITHOUT_CRASH}. Whether a finalized
 * launch reached the title screen or joined a world is retained separately on
 * {@link ModListHistoryRecord}.</p>
 */
public enum ModListHistoryStatus {
    STARTED,
    TITLE_SCREEN,
    JOINED,
    CRASHED_DURING_GAMEPLAY,
    CLOSED_WITHOUT_CRASH
}
