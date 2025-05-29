package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


public class LogComparator implements Comparator<Log> {

    // Define the custom order for log types
    private static final List<LogType> LOG_TYPE_ORDER = Arrays.asList(
            LogType.LOG,
            LogType.DEBUG_LOG,
            LogType.HS_ERR,
            LogType.CRASH_REPORT,
            LogType.DISCONNECT_CLIENT,
            LogType.WIN_EVENT,
            LogType.LAUNCHER_LOG,
            LogType.MIXER_LOGGER,
            LogType.KUBE_JS,
            LogType.CRAFT_TWEAKER,
            LogType.REI,
            LogType.CRASH_ASSISTANT,
            LogType.MOD_LIST
    );

    @Override
    public int compare(Log log1, Log log2) {
        // Compare based on the custom type order
        int typeComparison = Integer.compare(
                LOG_TYPE_ORDER.indexOf(log1.getType()),
                LOG_TYPE_ORDER.indexOf(log2.getType())
        );
        if (typeComparison != 0) {
            return typeComparison;
        }
        // If types are the same, compare by name
        return log1.getName().compareTo(log2.getName());
    }
}

