package dev.kostromdan.mods.crash_assistant.app.class_loading;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.history.ModListHistoryManager;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.nio.file.Paths;

/** One-shot process that releases all mod-parser memory when it exits. */
public final class ModListSnapshotWorker {
    public static final String RESULT_PREFIX = "CRASH_ASSISTANT_MODLIST_SNAPSHOT_RESULT:";

    private ModListSnapshotWorker() {
    }

    public static void run(String[] args) {
        applyPlatformArguments(args);
        ModListHistoryManager.SnapshotResult result =
                ModListHistoryManager.createSnapshot(Boot.parentStarted);
        System.out.println(RESULT_PREFIX
                + result.getHistoryTimestamp()
                + ":"
                + result.isModListTxtGenerated());
    }

    private static void applyPlatformArguments(String[] args) {
        String customLatestLogPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("-platform".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.platform = PlatformHelp.valueOf(args[++i]);
            } else if ("-platformOwerridenData".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.platform.deserializePlatformData(args[++i]);
            } else if ("-loaderJarName".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.loaderJarName = args[++i];
            } else if ("-minecraftVersion".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.minecraftVersion = args[++i];
            } else if ("-customLatestLogPath".equals(args[i]) && i + 1 < args.length) {
                customLatestLogPath = args[++i];
            }
        }
        if (customLatestLogPath != null) {
            ModListUtils.MODS_FOLDER = Paths.get(customLatestLogPath).getParent().getParent()
                    .resolve("mods").resolve("fabric-" + PlatformHelp.minecraftVersion);
        }
    }
}
