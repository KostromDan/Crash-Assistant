package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;

/** Coordinates the one history snapshot owned by the companion app process. */
public final class ModListHistoryManager {
    private static final Logger LOGGER = LogManager.getLogger(ModListHistoryManager.class);
    private static final ModListHistoryStore STORE = ModListHistoryStore.getDefault();
    private static final Path MODLIST_TXT = Paths.get("logs", "modlist.txt");

    private static long currentLaunchStartedAt = -1L;
    private static boolean initialized;
    private static ModListHistoryRecord currentRecord;
    private static boolean milestonePollingComplete;

    private ModListHistoryManager() {
    }

    /**
     * Captures the launch mod list once and uses that exact collection for both
     * the history JSON and {@code logs/modlist.txt}.
     */
    public static synchronized Optional<ModListHistoryRecord> initialize(long launchStartedAt) {
        if (initialized) {
            return getCurrentRecord();
        }
        currentLaunchStartedAt = launchStartedAt > 0L
                ? launchStartedAt
                : System.currentTimeMillis();

        if (!STORE.hasHistoryFiles()) {
            try {
                STORE.preserveLegacyText(MODLIST_TXT);
            } catch (Exception e) {
                LOGGER.error("Failed to preserve the pre-history modlist.txt before replacement", e);
            }
        }

        // Capture once even when the history directory is temporarily
        // unwritable: modlist.txt must still be generated for this launch.
        LinkedHashSet<Mod> mods = ModListUtils.getCurrentModList(true);
        currentRecord = ModListHistoryRecord.started(currentLaunchStartedAt, mods);
        boolean currentRecordPersisted = false;
        try {
            currentRecord = STORE.beginLaunchAtAvailableTimestamp(currentLaunchStartedAt, mods);
            currentLaunchStartedAt = currentRecord.getTimestamp();
            currentRecordPersisted = true;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize mod-list launch history", e);
        }
        initialized = true;
        milestonePollingComplete = currentRecord.hasReachedJoined()
                || currentRecord.getStatus() == ModListHistoryStatus.CRASHED_DURING_GAMEPLAY
                || currentRecord.getStatus() == ModListHistoryStatus.CLOSED_WITHOUT_CRASH;
        if (currentRecordPersisted) {
            LOGGER.info("Captured mod-list history for launch {} in {}",
                    currentLaunchStartedAt, ModListHistoryStore.getHistoryDirectory());
        }

        // Create the current launch file first, so a legacy file whose
        // lastModified happens to equal Boot.parentStarted cannot claim the
        // current launch's required filename.
        if (currentRecordPersisted) {
            try {
                Optional<ModListHistoryRecord> migrated =
                        PlatformHelp.isLinkDefault()
                                ? STORE.migrateLegacySnapshot(ModListUtils.getSavedModListPath())
                                : STORE.copyLegacySnapshot(ModListUtils.getSavedModListPath());
                if (migrated.isPresent()) {
                    LOGGER.info("Preserved the pre-history modlist.json in {}",
                            ModListHistoryStore.getHistoryDirectory());
                }
            } catch (Exception e) {
                // The source is deliberately kept when parsing, writing, or
                // verification fails.
                LOGGER.error("Failed to migrate the legacy standalone modlist.json; keeping the source file", e);
            }
        }

        try {
            // Use the same single scan that populated this launch record.
            Mod.writeModlistTxt(MODLIST_TXT, currentRecord.getMods());
        } catch (Exception e) {
            LOGGER.error("Failed to generate logs/modlist.txt for this launch", e);
        }
        return Optional.of(currentRecord);
    }

    public static synchronized void refreshMilestoneSignals(long minecraftPid) {
        if (!initialized || currentRecord == null || milestonePollingComplete) {
            return;
        }
        try {
            boolean titleScreenSignal = ProcessSignalIO.exists("successful_launch", minecraftPid);
            boolean joinedWorldSignal = ProcessSignalIO.exists("joined_world", minecraftPid);
            boolean reachedTitle = currentRecord.hasReachedTitleScreen() || titleScreenSignal;
            boolean reachedJoined = currentRecord.hasReachedJoined() || joinedWorldSignal;
            boolean changed = false;
            if (reachedTitle && !currentRecord.hasReachedTitleScreen()) {
                currentRecord = currentRecord.withMilestone(
                        ModListHistoryStatus.TITLE_SCREEN, true, false);
                changed = true;
            }
            if (reachedJoined && !currentRecord.hasReachedJoined()) {
                if (!reachedTitle) {
                    try {
                        ModListUtils.autoUpdateModpackModListAfterDirectJoin();
                    } catch (Exception e) {
                        // Baseline maintenance is best-effort and must never
                        // prevent the trustworthy JOINED milestone itself.
                        LOGGER.error("Failed to auto-update modlist.json after Direct Connect", e);
                    }
                }
                currentRecord = currentRecord.withMilestone(
                        ModListHistoryStatus.JOINED,
                        currentRecord.hasReachedTitleScreen(),
                        true);
                milestonePollingComplete = true;
                changed = true;
            }
            if (changed) {
                persistCurrentRecord("update mod-list launch milestone");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update mod-list launch milestone", e);
        }
    }

    public static synchronized void markCrashAssistantOpened() {
        if (!initialized) {
            return;
        }
        currentRecord = currentRecord.withCrashAssistantOpened();
        milestonePollingComplete = true;
        persistCurrentRecord("mark the mod-list launch as crashed during gameplay");
    }

    public static synchronized void markClosedWithoutCrash() {
        if (!initialized) {
            return;
        }
        currentRecord = currentRecord.withClosedWithoutCrash();
        milestonePollingComplete = true;
        persistCurrentRecord("mark the mod-list launch as closed without crash");
    }

    public static synchronized Optional<ModListHistoryRecord> getCurrentRecord() {
        if (currentLaunchStartedAt < 0L || currentRecord == null) {
            return Optional.empty();
        }
        return Optional.of(currentRecord);
    }

    public static ModListHistoryStore getStore() {
        return STORE;
    }

    private static boolean persistCurrentRecord(String operation) {
        try {
            STORE.save(currentRecord);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to {}", operation, e);
            return false;
        }
    }
}
