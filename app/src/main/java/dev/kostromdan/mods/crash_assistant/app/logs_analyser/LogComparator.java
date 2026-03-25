package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;

import java.util.*;


public class LogComparator implements Comparator<Log> {

    // Define the custom order for log types
    private static final List<LogType> DEFAULT_LOG_TYPE_ORDER = Arrays.asList(
            LogType.LOG,
            LogType.DEBUG_LOG,
            LogType.HS_ERR,
            LogType.STDERR_STREAM,
            LogType.CRASH_REPORT,
            LogType.DISCONNECT_CLIENT,
            LogType.WIN_EVENT,
            LogType.LAUNCHER_LOG,
            LogType.MIXER_LOGGER,
            LogType.KUBE_JS,
            LogType.CRAFT_TWEAKER,
            LogType.REI,
            LogType.GROOVY,
            LogType.BTW,
            LogType.CRASH_ASSISTANT,
            LogType.MOD_LIST,
            LogType.STARTUP_SCRIPTS
    );

    private static final List<LogType> FINAL_LOG_TYPE_ORDER = new ArrayList<>();

    static {
        List<String> logTypePriorityOverridesFromConfig = CrashAssistantConfig.getPriorityOverridesForLogsOrder();
        HashSet<LogType> addedLogTypes = new HashSet<>();
        for (String logTypeString : logTypePriorityOverridesFromConfig) {
            try {
                LogType logType = Enum.valueOf(LogType.class, logTypeString);
                if (addedLogTypes.contains(logType)) {
                    CrashAssistantApp.LOGGER.warn("Duplicated key in \"general.logs_priority_overrides\": \"{}\", skipping...", logTypeString);
                    continue;
                }
                FINAL_LOG_TYPE_ORDER.add(logType);
                addedLogTypes.add(logType);
            } catch (IllegalArgumentException e) {
                CrashAssistantApp.LOGGER.error("Cannot find LogType: \"{}\", skipping... Seems like invalid configuration in \"general.logs_priority_overrides\"", logTypeString);
            }
        }
        for (LogType logType : DEFAULT_LOG_TYPE_ORDER) {
            if (addedLogTypes.contains(logType)) continue;
            FINAL_LOG_TYPE_ORDER.add(logType);
        }
    }


    @Override
    public int compare(Log log1, Log log2) {
        // Compare based on the custom type order
        int typeComparison = Integer.compare(
                FINAL_LOG_TYPE_ORDER.indexOf(log1.getType()),
                FINAL_LOG_TYPE_ORDER.indexOf(log2.getType())
        );
        if (typeComparison != 0) {
            return typeComparison;
        }
        // If types are the same, compare by name
        return log1.getName().compareTo(log2.getName());
    }

    public static int compareLogTypes(LogType type1, LogType type2) {
        return Integer.compare(
                FINAL_LOG_TYPE_ORDER.indexOf(type1),
                FINAL_LOG_TYPE_ORDER.indexOf(type2)
        );
    }
}

