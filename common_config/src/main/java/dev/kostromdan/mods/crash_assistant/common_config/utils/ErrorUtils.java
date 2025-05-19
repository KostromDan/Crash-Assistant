package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for error handling.
 */
public final class ErrorUtils {

    private ErrorUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Returns the full error message from the given Throwable,
     * including the stack trace (as typically logged).
     *
     * @param error the error (exception or throwable)
     * @return the formatted string with error message and stack trace
     */
    public static String getErrorMessageAndStackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        return sw.toString();
    }
}