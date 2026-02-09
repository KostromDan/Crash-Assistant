package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class PhysX_64 extends KnownCrashReason {
    public PhysX_64() {
        super(
                LogType.LAUNCHER_LOG,
                LanguageProvider.get("warnings.physics")
        );
    }

    @Override
    public boolean matches(Log log) {
        log.getReader().readLogFileSafe();
        String logText = log.getReader().getAllLinesString();
        return logText.contains("ModuleName: PhysX_64.dll");
    }
}
