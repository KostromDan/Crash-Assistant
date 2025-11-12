package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.util.*;

public class LauncherLogReader extends LogReader {
    public LauncherLogReader(Log log) {
        super(log);
    }

    @Override
    public synchronized List<String> getAllLinesList() {
        synchronized (this) {
            if (allLinesListCached == null) {
                allLinesListCached = new ArrayList<>();
                allLinesListCached.addAll(firstLines);
                if (lastLines != null) allLinesListCached.addAll(lastLines);
                cutLauncherLog();
            }
            return allLinesListCached;
        }
    }

    private void cutLauncherLog() {
        if (Objects.equals(log.getName(), "stdout_stderr_streams.log")) {
            return;
        }
        allLinesListCached = Collections.singletonList("");
    }
}
