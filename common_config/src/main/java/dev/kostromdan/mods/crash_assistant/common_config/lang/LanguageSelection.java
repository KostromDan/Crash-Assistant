package dev.kostromdan.mods.crash_assistant.common_config.lang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class LanguageSelection {
    static final String SYSTEM_SOURCE = "SYSTEM";
    static final String GAME_SOURCE = "GAME";
    private static final String ENGLISH_LANGUAGE = "en_us";

    private LanguageSelection() {
    }

    static String selectLanguage(
            String source,
            String defaultLanguage,
            Locale systemLocale,
            String gameLanguage,
            Collection<String> availableLanguages
    ) {
        String selectedLanguage = selectLanguageFromSource(
                source,
                systemLocale,
                gameLanguage,
                availableLanguages
        );
        return selectedLanguage != null
                ? selectedLanguage
                : selectFallbackLanguage(defaultLanguage, availableLanguages);
    }

    static String selectLanguageFromSource(
            String source,
            Locale systemLocale,
            String gameLanguage,
            Collection<String> availableLanguages
    ) {
        String normalizedSource = source == null ? "" : source.trim();

        if (SYSTEM_SOURCE.equalsIgnoreCase(normalizedSource)) {
            return findSystemLanguage(systemLocale, availableLanguages);
        }
        if (GAME_SOURCE.equalsIgnoreCase(normalizedSource)) {
            return findExactLanguage(gameLanguage, availableLanguages);
        }
        return findExactLanguage(normalizedSource, availableLanguages);
    }

    static String selectFallbackLanguage(String defaultLanguage, Collection<String> availableLanguages) {
        String fallbackLanguage = findExactLanguage(defaultLanguage, availableLanguages);
        return fallbackLanguage != null ? fallbackLanguage : ENGLISH_LANGUAGE;
    }

    static boolean usesGameLanguage(String source) {
        return source != null && GAME_SOURCE.equalsIgnoreCase(source.trim());
    }

    static String getEffectiveSource(String configuredSource, boolean standaloneApp) {
        return standaloneApp ? configuredSource : GAME_SOURCE;
    }

    static String readGameLanguage(Path optionsPath) throws IOException {
        if (optionsPath == null || !Files.exists(optionsPath)) {
            return null;
        }

        List<String> lines = Files.readAllLines(optionsPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.startsWith("lang:")) {
                return normalizeLanguageCode(line.substring("lang:".length()));
            }
        }
        return null;
    }

    static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null) {
            return null;
        }
        String normalized = languageCode.trim()
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String findSystemLanguage(Locale systemLocale, Collection<String> availableLanguages) {
        if (systemLocale == null) {
            return null;
        }

        String language = normalizeLanguageCode(systemLocale.getLanguage());
        if (language == null) {
            return null;
        }

        String country = normalizeLanguageCode(systemLocale.getCountry());
        if (country != null) {
            String exactMatch = findExactLanguage(language + "_" + country, availableLanguages);
            if (exactMatch != null) {
                return exactMatch;
            }
        }

        String languageOnlyMatch = findExactLanguage(language, availableLanguages);
        if (languageOnlyMatch != null) {
            return languageOnlyMatch;
        }

        String regionalPrefix = language + "_";
        String regionalMatch = null;
        if (availableLanguages != null) {
            for (String availableLanguage : availableLanguages) {
                String normalizedLanguage = normalizeLanguageCode(availableLanguage);
                if (normalizedLanguage != null
                        && normalizedLanguage.startsWith(regionalPrefix)
                        && (regionalMatch == null || normalizedLanguage.compareTo(regionalMatch) < 0)) {
                    regionalMatch = normalizedLanguage;
                }
            }
        }
        return regionalMatch;
    }

    private static String findExactLanguage(String languageCode, Collection<String> availableLanguages) {
        String normalizedLanguageCode = normalizeLanguageCode(languageCode);
        if (normalizedLanguageCode == null || availableLanguages == null) {
            return null;
        }

        for (String availableLanguage : availableLanguages) {
            if (normalizedLanguageCode.equals(normalizeLanguageCode(availableLanguage))) {
                return normalizedLanguageCode;
            }
        }
        return null;
    }
}
