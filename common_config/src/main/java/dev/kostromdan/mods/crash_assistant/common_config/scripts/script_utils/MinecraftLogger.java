package dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils;

import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;
import org.apache.logging.log4j.LogManager;


public class MinecraftLogger {
    private static final org.apache.logging.log4j.Logger STATIC_LOGGER;

    static {
        if (ClassExistenceChecker.classExists("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp")) {
            STATIC_LOGGER = null; // On LogAnalysis stage should not be used and must be unavailable.
        } else {
            STATIC_LOGGER = LogManager.getLogger("StartupScripts");
        }
    }

    public static void info(CharSequence message) {
        STATIC_LOGGER.info(message);
    }

    public static void info(CharSequence message, Throwable throwable) {
        STATIC_LOGGER.info(message, throwable);
    }

    public static void info(Object message) {
        STATIC_LOGGER.info(message);
    }

    public static void info(Object message, Throwable throwable) {
        STATIC_LOGGER.info(message, throwable);
    }

    public static void info(String message) {
        STATIC_LOGGER.info(message);
    }

    public static void info(String message, Object... params) {
        STATIC_LOGGER.info(message, params);
    }

    public static void info(String message, Throwable throwable) {
        STATIC_LOGGER.info(message, throwable);
    }

    public static void warn(CharSequence message) {
        STATIC_LOGGER.warn(message);
    }

    public static void warn(CharSequence message, Throwable throwable) {
        STATIC_LOGGER.warn(message, throwable);
    }

    public static void warn(Object message) {
        STATIC_LOGGER.warn(message);
    }

    public static void warn(Object message, Throwable throwable) {
        STATIC_LOGGER.warn(message, throwable);
    }

    public static void warn(String message) {
        STATIC_LOGGER.warn(message);
    }

    public static void warn(String message, Object... params) {
        STATIC_LOGGER.warn(message, params);
    }

    public static void warn(String message, Throwable throwable) {
        STATIC_LOGGER.warn(message, throwable);
    }

    public static void error(CharSequence message) {
        STATIC_LOGGER.error(message);
    }

    public static void error(CharSequence message, Throwable throwable) {
        STATIC_LOGGER.error(message, throwable);
    }

    public static void error(Object message) {
        STATIC_LOGGER.error(message);
    }

    public static void error(Object message, Throwable throwable) {
        STATIC_LOGGER.error(message, throwable);
    }

    public static void error(String message) {
        STATIC_LOGGER.error(message);
    }

    public static void error(String message, Object... params) {
        STATIC_LOGGER.error(message, params);
    }

    public static void error(String message, Throwable throwable) {
        STATIC_LOGGER.error(message, throwable);
    }
}