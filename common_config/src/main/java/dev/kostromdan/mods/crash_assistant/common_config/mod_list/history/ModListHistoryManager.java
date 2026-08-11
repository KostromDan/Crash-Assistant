package dev.kostromdan.mods.crash_assistant.common_config.mod_list.history;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;

/** Coordinates the one history snapshot owned by a Minecraft launch. */
public final class ModListHistoryManager {
    private static final Logger LOGGER = LogManager.getLogger(ModListHistoryManager.class);
    private static final ModListHistoryStore STORE = ModListHistoryStore.getDefault();
    private static final Path MODLIST_TXT = Paths.get("logs", "modlist.txt");

    private static long currentLaunchStartedAt = -1L;
    private static ModListHistoryStatus currentStatus;
    private static boolean initialized;
    private static boolean historyAvailable;
    private static boolean modListTxtGenerated;
    private static boolean milestonePollingComplete;

    private ModListHistoryManager() {
    }

    /** Heavy snapshot creation. This method is called only by the short-lived worker process. */
    public static SnapshotResult createSnapshot(long launchStartedAt) {
        try {
            return ModListUtils.withModListScanLock(() -> createSnapshotLocked(launchStartedAt));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create the mod-list launch snapshot", e);
        }
    }

    private static SnapshotResult createSnapshotLocked(long launchStartedAt) {
        long preferredTimestamp = launchStartedAt > 0L
                ? launchStartedAt
                : System.currentTimeMillis();
        LinkedHashSet<Mod> mods = ModListUtils.getCurrentModList(true);
        if (!ModListUtils.wasLastScanSuccessful()) {
            throw new IllegalStateException("Failed to scan the current mod list");
        }

        long historyTimestamp = -1L;
        boolean historyWritten = false;
        try {
            ModListHistoryRecord record = STORE.beginLaunchAtAvailableTimestamp(preferredTimestamp, mods);
            historyTimestamp = record.getTimestamp();
            historyWritten = true;
            LOGGER.info("Captured mod-list history for launch {} in {}",
                    historyTimestamp, ModListHistoryStore.getHistoryDirectory());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize mod-list launch history", e);
        }

        if (historyWritten) {
            try {
                Optional<ModListHistoryRecord> migrated = PlatformHelp.isLinkDefault()
                        ? STORE.migrateLegacySnapshot(ModListUtils.getSavedModListPath())
                        : STORE.copyLegacySnapshot(ModListUtils.getSavedModListPath());
                if (migrated.isPresent()) {
                    LOGGER.info("Preserved the pre-history modlist.json in {}",
                            ModListHistoryStore.getHistoryDirectory());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to migrate the legacy standalone modlist.json; keeping the source file", e);
            }
        }

        boolean txtWritten = false;
        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")
                && CrashAssistantConfig.getBoolean("modpack_modlist.add_modlist_txt_as_log")) {
            try {
                Mod.writeModlistTxt(MODLIST_TXT, mods);
                txtWritten = true;
            } catch (Exception e) {
                LOGGER.error("Failed to generate logs/modlist.txt for this launch", e);
            }
        }
        return new SnapshotResult(historyTimestamp, txtWritten);
    }

    /** Attaches the lightweight waiter to the result produced by the worker. */
    public static synchronized void attachSnapshot(long historyTimestamp, boolean txtGenerated) {
        initialized = true;
        currentLaunchStartedAt = historyTimestamp;
        historyAvailable = historyTimestamp >= 0L;
        currentStatus = historyAvailable ? ModListHistoryStatus.STARTED : null;
        modListTxtGenerated = txtGenerated;
        milestonePollingComplete = false;
    }

    public static synchronized void refreshMilestoneSignals(long minecraftPid) {
        if (!initialized || milestonePollingComplete) {
            return;
        }
        try {
            boolean titleScreenSignal = ProcessSignalIO.exists("successful_launch", minecraftPid);
            boolean joinedWorldSignal = ProcessSignalIO.exists("joined_world", minecraftPid);
            if (titleScreenSignal && currentStatus == ModListHistoryStatus.STARTED) {
                setStatus(ModListHistoryStatus.TITLE_SCREEN,
                        "update the mod-list launch title-screen milestone");
            }
            if (joinedWorldSignal && currentStatus != ModListHistoryStatus.JOINED) {
                if (setStatus(ModListHistoryStatus.JOINED,
                        "update the mod-list launch joined-world milestone")) {
                    milestonePollingComplete = true;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to update mod-list launch milestone", e);
        }
    }

    public static synchronized void markCrashAssistantOpened() {
        if (!initialized) {
            return;
        }
        if (currentStatus == ModListHistoryStatus.JOINED) {
            if (setStatus(ModListHistoryStatus.CRASHED_DURING_GAMEPLAY,
                    "mark the mod-list launch as crashed during gameplay")) {
                milestonePollingComplete = true;
            }
        } else {
            milestonePollingComplete = true;
        }
    }

    public static synchronized void markClosedWithoutCrash() {
        if (!initialized || currentStatus == ModListHistoryStatus.CRASHED_DURING_GAMEPLAY
                || currentStatus != null && currentStatus.isClosedWithoutCrash()) {
            return;
        }
        ModListHistoryStatus closedStatus = currentStatus == ModListHistoryStatus.JOINED
                ? ModListHistoryStatus.CLOSED_WITHOUT_CRASH_AFTER_JOIN
                : currentStatus == ModListHistoryStatus.TITLE_SCREEN
                ? ModListHistoryStatus.CLOSED_WITHOUT_CRASH_AFTER_TITLE_SCREEN
                : ModListHistoryStatus.CLOSED_WITHOUT_CRASH;
        if (setStatus(closedStatus, "mark the mod-list launch as closed without crash")) {
            milestonePollingComplete = true;
        }
    }

    /** Loads the full record only when GUI code actually asks for it. */
    public static synchronized Optional<ModListHistoryRecord> getCurrentRecord() {
        if (!historyAvailable) {
            return Optional.empty();
        }
        return STORE.getRecord(currentLaunchStartedAt);
    }

    public static synchronized boolean wasModListTxtGenerated() {
        return modListTxtGenerated;
    }

    public static ModListHistoryStore getStore() {
        return STORE;
    }

    private static boolean setStatus(ModListHistoryStatus status, String operation) {
        if (currentStatus == status) {
            return true;
        }
        if (!historyAvailable) {
            currentStatus = status;
            return true;
        }
        Exception failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (!STORE.updateStatus(currentLaunchStartedAt, status)) {
                    historyAvailable = false;
                    currentStatus = status;
                    LOGGER.error("Failed to {}: history record {} does not exist",
                            operation, currentLaunchStartedAt);
                    return true;
                }
                currentStatus = status;
                return true;
            } catch (Exception e) {
                failure = e;
            }
        }
        LOGGER.error("Failed to {}", operation, failure);
        return false;
    }

    public static final class SnapshotResult {
        private final long historyTimestamp;
        private final boolean modListTxtGenerated;

        private SnapshotResult(long historyTimestamp, boolean modListTxtGenerated) {
            this.historyTimestamp = historyTimestamp;
            this.modListTxtGenerated = modListTxtGenerated;
        }

        public long getHistoryTimestamp() {
            return historyTimestamp;
        }

        public boolean isModListTxtGenerated() {
            return modListTxtGenerated;
        }
    }
}
