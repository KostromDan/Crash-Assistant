package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import com.electronwill.nightconfig.toml.TomlParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Validates TOML files using NightConfig's parser.
 */
public final class TomlNightConfigChecker implements ConfigChecker {

    private static final Set<String> EXTENSIONS = new LinkedHashSet<>(Arrays.asList("toml"));

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getName() {
        return "NightConfig TOML parser";
    }

    @Override
    public void check(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            new TomlParser().parse(reader);
        }
    }
}
