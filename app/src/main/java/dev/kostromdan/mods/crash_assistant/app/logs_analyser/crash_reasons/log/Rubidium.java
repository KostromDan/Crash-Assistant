package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;
import java.util.Objects;

public class Rubidium extends KnownCrashReason {
    public Rubidium() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.rubidium", new HashMap<String, String>() {{
                    put("$LINK.EMBEDDIUM$", "Embeddium");
                }})
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isForgeBased()) return false;
        return ModListUtils.getCurrentModList(true).stream().anyMatch(mod -> Objects.equals(mod.getModId(), "rubidium"));
    }
}
