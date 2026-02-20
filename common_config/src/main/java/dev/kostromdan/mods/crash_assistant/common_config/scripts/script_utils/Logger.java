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
    private static org.apache.logging.log4j.Logger GAME_LOGGER = LogManager.getLogger("StartupScripts");

    static {
        if (ClassExistenceChecker.classExists("dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp")) {
            STATIC_LOGGER = LogManager.getLogger("LogAnalysisScripts");
            GAME_LOGGER = null; // On LogAnalysis stage should not be used and must be unavailable.
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
        return GAME_LOGGER;
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


    public static class Game {
        public static void info(CharSequence message) {
            GAME_LOGGER.info(message);
        }

        public static void info(CharSequence message, Throwable throwable) {
            GAME_LOGGER.info(message, throwable);
        }

        public static void info(Object message) {
            GAME_LOGGER.info(message);
        }

        public static void info(Object message, Throwable throwable) {
            GAME_LOGGER.info(message, throwable);
        }

        public static void info(String message) {
            GAME_LOGGER.info(message);
        }

        public static void info(String message, Object... params) {
            GAME_LOGGER.info(message, params);
        }

        public static void info(String message, Throwable throwable) {
            GAME_LOGGER.info(message, throwable);
        }

        public static void warn(CharSequence message) {
            GAME_LOGGER.warn(message);
        }

        public static void warn(CharSequence message, Throwable throwable) {
            GAME_LOGGER.warn(message, throwable);
        }

        public static void warn(Object message) {
            GAME_LOGGER.warn(message);
        }

        public static void warn(Object message, Throwable throwable) {
            GAME_LOGGER.warn(message, throwable);
        }

        public static void warn(String message) {
            GAME_LOGGER.warn(message);
        }

        public static void warn(String message, Object... params) {
            GAME_LOGGER.warn(message, params);
        }

        public static void warn(String message, Throwable throwable) {
            GAME_LOGGER.warn(message, throwable);
        }

        public static void error(CharSequence message) {
            GAME_LOGGER.error(message);
        }

        public static void error(CharSequence message, Throwable throwable) {
            GAME_LOGGER.error(message, throwable);
        }

        public static void error(Object message) {
            GAME_LOGGER.error(message);
        }

        public static void error(Object message, Throwable throwable) {
            GAME_LOGGER.error(message, throwable);
        }

        public static void error(String message) {
            GAME_LOGGER.error(message);
        }

        public static void error(String message, Object... params) {
            GAME_LOGGER.error(message, params);
        }

        public static void error(String message, Throwable throwable) {
            GAME_LOGGER.error(message, throwable);
        }
    }
}