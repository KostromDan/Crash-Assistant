package dev.kostromdan.mods.crash_assistant.common_config.utils;

import org.apache.logging.log4j.LogManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class LatestLogLocator {

    private LatestLogLocator() {
    }

    /**
     * Tries to find absolute path to logs/latest.log using Log4j2 configuration.
     * Works even if log4j-core is loaded in a different ClassLoader (e.g. Mixin, launcher, etc.).
     *
     * @return absolute Path to logs/latest.log, or null if not found or on error.
     */
    public static Path findLatestLogPath() {
        Object ctx = LogManager.getContext(false);
        if (ctx == null) {
            return null;
        }

        try {
            // ctx.getConfiguration()
            Method getConfigurationMethod = ctx.getClass().getMethod("getConfiguration");
            Object config = getConfigurationMethod.invoke(ctx);
            if (config == null) {
                return null;
            }

            // config.getAppenders() -> Map<String, Appender>
            Method getAppendersMethod = config.getClass().getMethod("getAppenders");
            Object appendersObj = getAppendersMethod.invoke(config);
            if (!(appendersObj instanceof Map)) {
                return null;
            }

            Map appenders = (Map) appendersObj;
            Path suffix = Paths.get("logs", "latest.log");

            for (Object entryObj : appenders.entrySet()) {
                if (!(entryObj instanceof Map.Entry)) {
                    continue;
                }

                Map.Entry entry = (Map.Entry) entryObj;
                Object appender = entry.getValue();
                if (appender == null) {
                    continue;
                }

                String fileName = tryGetFileName(appender);
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }

                Path abs = Paths.get(fileName).toAbsolutePath().normalize();
                if (abs.endsWith(suffix) && Files.isRegularFile(abs)) {
                    return abs;
                }
            }
        } catch (NoSuchMethodException e) {
            // Log4j implementation is too different / not Log4j2
            return null;
        } catch (IllegalAccessException e) {
            // Access problem, just give up
            return null;
        } catch (InvocationTargetException e) {
            // Underlying method threw an exception
            return null;
        } catch (Throwable t) {
            // Any other unexpected failure — do not crash the game
            return null;
        }

        return null;
    }

    /**
     * Tries to call appender.getFileName() via reflection.
     * Works for FileAppender, RollingFileAppender, RollingRandomAccessFileAppender, etc.,
     * regardless of which ClassLoader loaded them.
     */
    private static String tryGetFileName(Object appender) {
        try {
            Method getFileNameMethod = appender.getClass().getMethod("getFileName");
            Object value = getFileNameMethod.invoke(appender);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (NoSuchMethodException e) {
            // Appender has no getFileName() -> not a file-based appender
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        } catch (Throwable t) {
            return null;
        }
        return null;
    }
}
