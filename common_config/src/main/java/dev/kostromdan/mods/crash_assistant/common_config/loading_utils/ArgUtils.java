package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.regex.Pattern;

public class ArgUtils {
    private static String launchArgs = null;

    public static String getSafeLaunchArgs() {
        if (launchArgs != null) return censor(launchArgs);
        return getLaunchArgsFallback();
    }

    public static String getLaunchArgsFallback() {
        JarInJarHelper.LOGGER.warn("Failed to get launch args, falling back to sun.java.command");
        String command = System.getProperty("sun.java.command");
        if (command == null) return "null";
        return censor(command);
    }

    public static String getSafeJvmArgs() {
        String args = String.join(", ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        return censor(args);
    }

    public static String getSafeClassPath() {
        return censor(getUnsafeClassPath());
    }

    public static String[] getSafeClassPathList() {
        return getSafeClassPath().split(Pattern.quote(File.pathSeparator));
    }

    @NoJexl
    public static String getUnsafeClassPath() {
        return System.getProperty("java.class.path");
    }

    @NoJexl
    public static String[] getUnsafeClassPathList() {
        return getUnsafeClassPath().split(Pattern.quote(File.pathSeparator));
    }

    protected static String censor(String input) {
        return censor(input, System.getProperty("user.name"));
    }

    static String censor(String input, String osUser) {
        if (input == null) return null;

        input = input.replaceAll("(--(?:accessToken|xuid|session)[\\s=:,]*)([^\\s,]+)", "$1????????");

        if (osUser == null || osUser.isEmpty()) {
            return input;
        }

        return censorPathUser(input, osUser);
    }

    private static String censorPathUser(String input, String osUser) {
        StringBuilder result = new StringBuilder(input.length());
        int index = 0;
        int userLength = osUser.length();

        while (true) {
            int found = input.indexOf(osUser, index);
            if (found < 0) {
                result.append(input, index, input.length());
                return result.toString();
            }

            boolean leftOk = found > 0 && isPathSeparator(input.charAt(found - 1));
            boolean rightOk = found + userLength == input.length()
                    || isRightPathBoundary(input.charAt(found + userLength));

            result.append(input, index, found);

            if (leftOk && rightOk) {
                result.append("<USER>");
            } else {
                result.append(osUser);
            }

            index = found + userLength;
        }
    }

    private static boolean isPathSeparator(char c) {
        return c == '/' || c == '\\';
    }

    private static boolean isRightPathBoundary(char c) {
        return isPathSeparator(c)
                || Character.isWhitespace(c)
                || c == '"'
                || c == '\''
                || c == ','
                || c == ';'
                || c == ')'
                || c == ']';
    }

    @NoJexl
    public static void setLaunchArgs(String args) {
        launchArgs = args;
    }

    @NoJexl
    public static void setLaunchArgs(String[] args) {
        setLaunchArgs(String.join(", ", args));
    }
}
