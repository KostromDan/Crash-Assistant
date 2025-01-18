package dev.kostromdan.mods.crash_assistant.app.utils;

import java.time.Instant;
import java.util.Optional;

public interface ProcessHelper {
    static long getStartTime(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return -1;
        return processHandle.get().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }
}
