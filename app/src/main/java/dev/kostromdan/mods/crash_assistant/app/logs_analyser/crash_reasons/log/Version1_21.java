package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.Objects;

public class Version1_21 extends KnownCrashReason {
    public Version1_21() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.version1_21")
        );
    }

    @Override
    public boolean matches(Log log) {
        return Objects.equals(PlatformHelp.minecraftVersion, "1.21");
    }
}