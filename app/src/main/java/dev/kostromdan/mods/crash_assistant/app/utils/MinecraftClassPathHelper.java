package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MinecraftClassPathHelper {
    private static final Logger LOGGER = LogManager.getLogger(MinecraftClassPathHelper.class);

    private MinecraftClassPathHelper() {
    }

    public static Stream<Path> streamCurrentClassPathArchives() {
        return streamClassPathArchives(Boot.MINECRAFT_CLASS_PATH);
    }

    public static Stream<Path> streamClassPathEntries(String classPath) {
        if (classPath == null || classPath.trim().isEmpty()) {
            return Stream.empty();
        }

        String currentUser = System.getProperty("user.name");
        String preparedClassPath = classPath.replace('\r', '\n');
        return Arrays.stream(preparedClassPath.split("\n"))
                .flatMap(line -> Arrays.stream(line.split(Pattern.quote(File.pathSeparator))))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(MinecraftClassPathHelper::trimWrappingQuotes)
                .map(entry -> restoreUserPlaceholder(entry, currentUser))
                .map(MinecraftClassPathHelper::parsePathOrNull)
                .filter(Objects::nonNull)
                .filter(Files::exists)
                .distinct();
    }

    public static Stream<Path> streamClassPathArchives(String classPath) {
        return streamClassPathEntries(classPath)
                .filter(Files::isRegularFile)
                .filter(MinecraftClassPathHelper::isArchivePath);
    }

    private static String restoreUserPlaceholder(String entry, String currentUser) {
        if (entry == null || !entry.contains("<USER>")) {
            return entry;
        }
        if (currentUser == null || currentUser.trim().isEmpty()) {
            return entry;
        }
        return entry.replace("<USER>", currentUser);
    }

    private static String trimWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        boolean wrappedWithDoubleQuotes = first == '"' && last == '"';
        boolean wrappedWithSingleQuotes = first == '\'' && last == '\'';
        if (wrappedWithDoubleQuotes || wrappedWithSingleQuotes) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path parsePathOrNull(String value) {
        try {
            return Paths.get(value);
        } catch (InvalidPathException e) {
            LOGGER.warn("Skipping invalid class path entry: {}", value);
            return null;
        }
    }

    private static boolean isArchivePath(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jar") || fileName.endsWith(".zip");
    }
}
