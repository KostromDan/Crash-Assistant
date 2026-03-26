package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonToken;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Validates JSON5 files using Quilt's JSON5 parser.
 */
public final class QuiltJson5Checker implements ConfigChecker {

    private static final Set<String> EXTENSIONS = new LinkedHashSet<>(Arrays.asList("json5"));

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public String getName() {
        return "Quilt JSON5 parser";
    }

    @Override
    public void check(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = JsonReader.json5(reader);
            // Just skip the root value. skipValue() in Quilt's JsonReader is recursive
            // and validates the structure along the way.
            jsonReader.skipValue();

            // After skipping the root value, we should be at the end of the document.
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new Exception("Extra content at the end of JSON5 file");
            }
        }
    }
}
