package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogAnalyser;

import java.util.ArrayList;
import java.util.List;

public class CodexMessage extends KnownCrashReason {
    public CodexMessage(String message) {
        super(LogAnalyser.CodexSupportedLogTypes, message, new ArrayList<>());
    }
}
