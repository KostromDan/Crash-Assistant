package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

/**
 * Validates JSON files using Google's Gson parser.
 */
public final class GsonJsonChecker implements ConfigChecker {

    private static final Set<String> EXTENSIONS = Collections.unmodifiableSet(Set.of("json", "json5"));

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getName() {
        return "Gson JSON parser";
    }

    @Override
    public void check(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonParser.parseReader(reader);
        }
    }
}
