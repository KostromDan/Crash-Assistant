package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import com.electronwill.nightconfig.json.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Validates JSON files using NightConfig's JSON parser.
 */
public final class NightConfigJsonChecker implements ConfigChecker {

    private static final Set<String> EXTENSIONS = Collections.unmodifiableSet(Set.of("json", "json5"));

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getName() {
        return "NightConfig JSON parser";
    }

    @Override
    public void check(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            new JsonParser().parse(reader);
        }
    }
}
