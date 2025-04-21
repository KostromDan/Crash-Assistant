package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;

public class MissingEmbeddiumForOculus extends KnownCrashReason {
    public MissingEmbeddiumForOculus() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.missing_embeddium_for_oculus", new HashMap<>() {{
                    put("$LINK.EMBEDDIUM$", "Embeddium");
                }}),
                ".*Error loading class: net/caffeinemc/mods/sodium/api/memory/MemoryIntrinsics \\(java\\.lang\\.ClassNotFoundException: net\\.caffeinemc\\.mods\\.sodium\\.api\\.memory\\.MemoryIntrinsics\\).*"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;
        LinkedHashSet<Mod> mods = ModListUtils.getCurrentModList(true);
        if (mods.stream().noneMatch(mod -> Objects.equals(mod.getModId(), "oculus"))) return false;
        if (mods.stream().anyMatch(mod ->
                Objects.equals(mod.getModId(), "embeddium") ||
                        Objects.equals(mod.getModId(), "xenon") ||
                        Objects.equals(mod.getModId(), "rubidium"))) return false;
        return super.matches(log);
    }
}