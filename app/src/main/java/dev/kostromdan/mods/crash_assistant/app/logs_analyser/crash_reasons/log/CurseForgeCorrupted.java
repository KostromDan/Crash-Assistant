package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.FilesRemover;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.CorruptedJarFinderGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.FileUtils;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

public class CurseForgeCorrupted extends KnownCrashReason {
    private static final Path CURSEFORGE_INSTALL_FOLDER = Paths.get("..", "..", "Install").toAbsolutePath().normalize();

    public CurseForgeCorrupted() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.curseforge_corrupted", new HashMap<String, String>() {{
                    put("$LINK.ATL$", "ATLauncher");
                }}),
                "Missing or unsupported mandatory dependencies:\\R\\s*Mod ID: 'minecraft', Requested by: .*?, Actual version: '\\[MISSING\\]'",
                "Missing or unsupported mandatory dependencies:\\R\\s*Mod ID: 'neoforge', Requested by: .*?, Actual version: '\\[MISSING\\]'",
                "Error loading class: net/minecraft/world/level/biome/Biome \\(java\\.lang\\.IllegalStateException: Unable to find method \\(\\)Lnet/minecraft/world/level/biome/"
        );
        this.priority = 2000;
        addAutoFixButtons(autoFixButtons);
    }

    @Override
    public boolean matches(Log log) {
        if (!FileUtils.isCurseForgeEnv()) return false;
        return super.matches(log);
    }

    public static void addAutoFixButtons(LinkedHashMap<String, Consumer<JDialog>> buttons) {
        buttons.put(LanguageProvider.get("gui.analysis.find_corrupted_installation_jar"), (dialog) -> CorruptedJarFinderGUI.showDialog((JFrame) dialog.getOwner()));
        buttons.put(LanguageProvider.get("gui.analysis.show_install_folder"), dialog -> {
            try {
                FilesRemover.revealInFileManager(CURSEFORGE_INSTALL_FOLDER);
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Failed to reveal CurseForge Install folder", e);
            }
        });
    }
}
