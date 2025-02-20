package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class KnownCrashReason {
    public static final List<KnownCrashReason> crashReasons = Collections.synchronizedList(new ArrayList<>());
    public boolean shownWarn;
    public String msg;
    public final Path logPath;

    public KnownCrashReason(Path logPath, String msg) {
        this.shownWarn = false;
        this.msg = msg;
        this.logPath = logPath;
        crashReasons.add(this);
    }

    public static void addIfContainsOneOfPatterns(Path logPath, String msg, String... patterns) {
        if (RegexChecker.logContainsOneOfPatterns(logPath, patterns)) {
            crashReasons.add(new KnownCrashReason(logPath, msg));
        }
    }

    public static void addIfContainsOneOfPatterns(Path logPath, String msg, Collection<String> patterns) {
        if (RegexChecker.logContainsOneOfPatterns(logPath, patterns)) {
            crashReasons.add(new KnownCrashReason(logPath, msg));
        }
    }

}
