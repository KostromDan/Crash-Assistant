package dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;

import java.io.InputStream;

public class Logger {
    private static final org.apache.logging.log4j.Logger STATIC_LOGGER;

    static {
        if (ClassExistenceChecker.classExists("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp")) {
            STATIC_LOGGER = LogManager.getLogger("LogAnalysisScripts");
        } else {
            STATIC_LOGGER = createStartupLogger();
        }
    }

    private static org.apache.logging.log4j.Logger createStartupLogger() {
        try (InputStream is = Logger.class.getResourceAsStream("/log4j2-startup.xml")) {
            if (is != null) {
                LoggerContext context = new LoggerContext("CrashAssistantStartupScripts");
                ConfigurationSource source = new ConfigurationSource(is);
                Configuration config = ConfigurationFactory.getInstance().getConfiguration(context, source);
                context.start(config);
                return context.getLogger("StartupScripts");
            }
        } catch (Throwable e) {
            JarInJarHelper.LOGGER.error("Error while loading log4j2-startup.xml", e);
        }
        return LogManager.getLogger("StartupScripts");
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

    public static void info(String message, Object p0) {
        STATIC_LOGGER.info(message, p0);
    }

    public static void info(String message, Object p0, Object p1) {
        STATIC_LOGGER.info(message, p0, p1);
    }

    public static void info(String message, Object p0, Object p1, Object p2) {
        STATIC_LOGGER.info(message, p0, p1, p2);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3, p4);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
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

    public static void warn(String message, Object p0) {
        STATIC_LOGGER.warn(message, p0);
    }

    public static void warn(String message, Object p0, Object p1) {
        STATIC_LOGGER.warn(message, p0, p1);
    }

    public static void warn(String message, Object p0, Object p1, Object p2) {
        STATIC_LOGGER.warn(message, p0, p1, p2);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
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

    public static void error(String message, Object p0) {
        STATIC_LOGGER.error(message, p0);
    }

    public static void error(String message, Object p0, Object p1) {
        STATIC_LOGGER.error(message, p0, p1);
    }

    public static void error(String message, Object p0, Object p1, Object p2) {
        STATIC_LOGGER.error(message, p0, p1, p2);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3, p4);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6, p7);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }
}
