package dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed;

import org.apache.logging.log4j.LogManager;

/**
 * A static Logger for scripts.
 * Logs are prefixed with "Script".
 */
public class Logger {
    private static final org.apache.logging.log4j.Logger STATIC_LOGGER = LogManager.getLogger("Script");

    public static void info(String message) {
        STATIC_LOGGER.info(message);
    }

    public static void info(String message, Object... params) {
        STATIC_LOGGER.info(message, params);
    }

    public static void warn(String message) {
        STATIC_LOGGER.warn(message);
    }

    public static void warn(String message, Object... params) {
        STATIC_LOGGER.warn(message, params);
    }

    public static void error(String message) {
        STATIC_LOGGER.error(message);
    }

    public static void error(String message, Object... params) {
        STATIC_LOGGER.error(message, params);
    }
}
