package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static registry for configuration checkers grouped by extension.
 */
public final class ConfigCheckerRegistry {

    private static final List<ConfigChecker> CHECKERS = Arrays.asList(
            new TomlNightConfigChecker(),
            new NightConfigJsonChecker(),
            new GsonJsonChecker(),
            new QuiltJson5Checker(),
            new QuiltJsoncChecker(),
            new PropertiesConfigChecker()
    );

    private static final Map<String, List<ConfigChecker>> CHECKERS_BY_EXTENSION;
    private static final Set<String> SUPPORTED_EXTENSIONS;

    static {
        Map<String, List<ConfigChecker>> byExtension = new LinkedHashMap<>();
        for (ConfigChecker checker : CHECKERS) {
            for (String extension : checker.supportedExtensions()) {
                byExtension.computeIfAbsent(extension, key -> new ArrayList<>()).add(checker);
            }
        }
        byExtension.replaceAll((ext, list) -> Collections.unmodifiableList(list));
        CHECKERS_BY_EXTENSION = Collections.unmodifiableMap(byExtension);
        SUPPORTED_EXTENSIONS = Collections.unmodifiableSet(new LinkedHashSet<>(CHECKERS_BY_EXTENSION.keySet()));
    }

    private ConfigCheckerRegistry() {
    }

    public static List<ConfigChecker> getCheckers(String extension) {
        List<ConfigChecker> checkers = CHECKERS_BY_EXTENSION.get(extension);
        return checkers == null ? Collections.emptyList() : checkers;
    }

    public static Set<String> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
}
