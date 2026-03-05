package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import org.apache.commons.jexl3.annotations.NoJexl;

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
        if (args == null) return "null";
        return censor(args);
    }

    private static String censor(String input) {
        if (input == null) return null;

        input = input.replaceAll("(--(accessToken|xuid)[\\s=:,]*)([^\\s,]+)", "$1????????");

        String osUser = System.getProperty("user.name");
        if (osUser != null && osUser.length() > 2) {
            String safeUser = Pattern.quote(osUser);
            String regex = "(?<!username[\\s=:])(?<!username,\\s)" + safeUser;
            input = input.replaceAll(regex, "<USER>");
        }

        return input;
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
