package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Validates .properties files using Java's standard Properties class.
 */
public final class PropertiesConfigChecker implements ConfigChecker {

    private static final Set<String> EXTENSIONS = new LinkedHashSet<>(Arrays.asList("properties"));

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getName() {
        return "Java Properties parser";
    }

    @Override
    public void check(Path path) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }
}
