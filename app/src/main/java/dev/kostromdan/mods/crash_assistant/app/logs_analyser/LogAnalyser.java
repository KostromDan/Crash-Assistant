package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.CodexAnalysis;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.codex.ErroringEntity;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.Create6Addons;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.CurseForgeCorrupted;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ResourceLocationException;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.OutOfMemoryError;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.win_event.WasClosedByWindows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class LogAnalyser {
    private static final List<KnownCrashReason> registeredReasons = new ArrayList<>();
    private static final List<CodexAnalysis> registeredCodexReasons = new ArrayList<>();
    public static final HashSet<LogType> CodexSupportedLogTypes = new HashSet<>() {{
        add(LogType.LOG);
        add(LogType.CRASH_REPORT);
    }};

    public static void registerKnownCrashReason(KnownCrashReason reason) {
        registeredReasons.add(reason);
    }

    public static void registerCodexKnownCrashReason(CodexAnalysis reason) {
        registeredCodexReasons.add(reason);
    }

    public static synchronized void analyseLogs() {
        synchronized (KnownCrashReasonMessage.class) {
            for (Log log : LogsList.getLogs()) {
                analyseLog(log);
            }
        }
    }

    public static synchronized void analyseLog(Log log) {
        if (log.isAnalysed()) return;
        registeredReasons.stream().filter(reason -> reason.getLogTypes().contains(log.getType())).forEach(reason -> {
            if (reason.matches(log)
//                    || true //debug
            ) {
                KnownCrashReasonMessage.addCrashReasonMessage(new KnownCrashReasonMessage(log, reason));
            }
        });
        log.setAnalysed(true);
    }

    public static synchronized String analyseCodexMessage(String message) {
        for (CodexAnalysis reason : registeredCodexReasons) {
            if (reason.matches(message)) {
                return reason.getMessage();
            }
        }
        return "";
    }

    public static void registerReasons() {
        registerKnownCrashReason(new Atio6axx());
        registerKnownCrashReason(new InsufficientMemory());
        registerKnownCrashReason(new Jemalloc());
        registerKnownCrashReason(new LibGLFWDotSo());
        registerKnownCrashReason(new LibOpenALDotSo());
        registerKnownCrashReason(new MacJDK());

        registerKnownCrashReason(new Create6Addons());
        registerKnownCrashReason(new CurseForgeCorrupted());
        registerKnownCrashReason(new OutOfMemoryError());
        registerKnownCrashReason(new ResourceLocationException());

        registerKnownCrashReason(new WasClosedByWindows());


        registerCodexKnownCrashReason(new ErroringEntity());
    }
}
