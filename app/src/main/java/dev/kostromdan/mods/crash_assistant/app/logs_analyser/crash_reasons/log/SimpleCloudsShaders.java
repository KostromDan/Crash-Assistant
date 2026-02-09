package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.Objects;

public class SimpleCloudsShaders extends KnownCrashReason {
    public SimpleCloudsShaders() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.simple_clouds_shaders")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isForgeBased()) return false;
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "simpleclouds")))
            return false;
        String logText = log.getReader().getAllLinesString();
        return logText.contains("Simple Clouds does not currently support shaders with Distant Horizons. Please either remove Oculus/Iris to play with Simple Clouds, or remove Simple Clouds to play with shaders.");

    }
}
