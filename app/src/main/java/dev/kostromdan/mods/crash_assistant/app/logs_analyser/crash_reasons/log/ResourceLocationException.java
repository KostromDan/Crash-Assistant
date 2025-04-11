package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.Objects;


public class ResourceLocationException extends KnownCrashReason {
    public ResourceLocationException() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.resource_location_exception"),
                "Caused by: net\\.minecraft\\.ResourceLocationException: Non \\[a-z0-9/\\._-\\] character in path of location: modular_machinery_reborn:casing_pla"
        );
    }

    @Override
    public boolean matches(String logText, Log log) {
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "modular_machinery_reborn"))) {
            return false;
        }
        return super.matches(logText, log);
    }
}
