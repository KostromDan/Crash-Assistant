package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.HashMap;
import java.util.Objects;

public class Optifine extends KnownCrashReason {
    public Optifine() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.optifine")
        );
    }

    @Override
    public boolean matches(Log log) {
        return ModListUtils.getCurrentModList(true).stream().anyMatch(mod -> Objects.equals(mod.getModId(), "optifine"));
    }
}