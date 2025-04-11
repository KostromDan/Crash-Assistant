package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.FileUtils;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashMap;

public class CurseForgeCorrupted extends KnownCrashReason {
    public CurseForgeCorrupted() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.curseforge_corrupted", new HashMap<>() {{
                    put("$LINK.ATL$", "ATLauncher");
                }}),
                "Missing or unsupported mandatory dependencies:\\R\\s*Mod ID: 'minecraft', Requested by: .*?, Actual version: '\\[MISSING\\]'",
                "Missing or unsupported mandatory dependencies:\\R\\s*Mod ID: 'neoforge', Requested by: .*?, Actual version: '\\[MISSING\\]'",
                "Error loading class: net/minecraft/world/level/biome/Biome \\(java\\.lang\\.IllegalStateException: Unable to find method \\(\\)Lnet/minecraft/world/level/biome/BiomeSpecialEffects; with name getModifiedSpecialEffects in net/minecraft/world/level/biome/Biome\\)"
        );
    }

    @Override
    public boolean matches(String logText, Log log) {
        if (!FileUtils.isCurseForgeEnv()) {
            return false;
        }
        return super.matches(logText, log);
    }
}