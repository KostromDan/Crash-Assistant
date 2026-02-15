package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;

public class ScriptedAnalysis extends KnownCrashReason {
    public ScriptedAnalysis(LogType logType, String message) {
        super(logType, message, new String[0]);
        priority = 10000;
    }

    @Override
    public String getReasonName() {
        return message != null ? message : super.getReasonName();
    }

    @Override
    public boolean matches(dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log log) {
        return true;
    }
}
