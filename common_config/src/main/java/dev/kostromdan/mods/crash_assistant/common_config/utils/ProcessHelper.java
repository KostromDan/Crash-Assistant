package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.lang.ProcessHandle;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

public interface ProcessHelper {
    static long getCurrentProcessId() {
        return ProcessHandle.current().pid();
    }

    static Optional<String> getCurrentProcessCommand() {
        return ProcessHandle.current().info().command();
    }

    static long getCurrentProcessStartTime() {
        return ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    static long getProcessStartTime(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return -1;
        return processHandle.get().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
    }

    static boolean isProcessAlive(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        return processHandle.isPresent() && processHandle.get().isAlive();
    }

    static String getChildProcessesInfo() {
        return String.join("\n", ProcessHandle.current().children()
                .map(child -> child.pid() + ": " + child.info().startInstant().get().toEpochMilli())
                .collect(Collectors.toList()));
    }

    static boolean destroyProcess(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return false;
        return processHandle.get().destroy();
    }

    static boolean destroyProcessForcibly(long pid) {
        Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
        if (processHandle.isEmpty()) return false;
        return processHandle.get().destroyForcibly();
    }
}
