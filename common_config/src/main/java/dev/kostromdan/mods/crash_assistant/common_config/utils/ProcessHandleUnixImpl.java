package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.util.Optional;

/**
 * Unix/Linux-specific implementation of ProcessHandleAbstractImpl.
 * Uses Unix commands like 'ps', 'kill', etc. to manage processes.
 */
public class ProcessHandleUnixImpl extends ProcessHandleAbstractImpl {

    @Override
    public Optional<String> getCurrentProcessCommand() {
        return Optional.empty();
    }

    @Override
    public long getCurrentProcessStartTime() {
        return 0;
    }

    @Override
    public long getProcessStartTime(long pid) {
        return 0;
    }

    @Override
    public boolean isProcessAlive(long pid) {
        return false;
    }

    @Override
    public String getChildProcessesInfo() {
        return "";
    }

    @Override
    public boolean destroyProcess(long pid) {
        return false;
    }

    @Override
    public boolean destroyProcessForcibly(long pid) {
        return false;
    }
}