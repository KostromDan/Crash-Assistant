package dev.kostromdan.mods.crash_assistant.app.utils.uploading_apis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans all mods (including jar-in-jar) for dotted numeric chains (length ≥ 4) inside versions and jar names.
 * For each full chain, stores a mapping from the original chain to a masked variant where every ASCII dot is
 * replaced with U+2219 (∙). Applying the map prevents IP-like censoring.
 */
public final class McLogsAntiVersionCensorer {

    private McLogsAntiVersionCensorer() {
    }

    private static final char SAFE_DOT = '\u2219'; // U+2219 BULLET OPERATOR "∙"
    private static final Pattern DOTTED_CHAIN = Pattern.compile("(?<!\\d)(?:\\d{1,3}\\.){3,}\\d{1,3}(?!\\d)");

    /**
     * Lazily built on first apply(); null until built.
     */
    private static volatile Map<String, String> IP_LIKE_VERSION_REPLACEMENTS = null;

    /**
     * Replaces each detected chain with its fully masked form. Thread-safe and idempotent.
     * First call builds the replacements map from the current mod list.
     */
    public static synchronized String apply(String text) {
        if (!CrashAssistantConfig.getBoolean("general.enable_mclogs_anti_ip_like_version_censorer")) {
            return text;
        }
        if (text == null || text.isEmpty()) return text;
        ensureBuilt();
        String out = text;
        for (Map.Entry<String, String> e : IP_LIKE_VERSION_REPLACEMENTS.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Forces a rebuild from the current mod list. Thread-safe.
     */
    public static synchronized void computeIpLikeVersions() {
        buildMap();
    }

    /**
     * Returns the current mapping (null until first build).
     */
    public static Map<String, String> getIpLikeVersionReplacements() {
        return IP_LIKE_VERSION_REPLACEMENTS;
    }

    /* ------------------------------- internals ------------------------------- */

    private static void ensureBuilt() {
        if (IP_LIKE_VERSION_REPLACEMENTS == null) {
            buildMap();
        }
    }

    private static void buildMap() {
        final Set<String> chains = new LinkedHashSet<>();

        final Consumer<Mod> visit = mod -> {
            if (mod == null) return;
            collectChains(nz(mod.getVersion()), chains);
            collectChains(nz(mod.getJarName()), chains);
        };

        final LinkedHashSet<Mod> roots = ModListUtils.getCurrentModList(true);
        if (roots != null) {
            for (Mod m : roots) traverseDepthFirst(m, visit);
        }

        final Map<String, String> map = new LinkedHashMap<>(Math.max(16, chains.size() * 2));
        for (String chain : chains) {
            map.put(chain, maskAllDots(chain));
        }
        IP_LIKE_VERSION_REPLACEMENTS = Collections.unmodifiableMap(map);
        CrashAssistantApp.LOGGER.info("IP-like version replacements computed: {}", IP_LIKE_VERSION_REPLACEMENTS.keySet());
    }

    private static void traverseDepthFirst(Mod mod, Consumer<Mod> visitor) {
        if (mod == null) return;
        visitor.accept(mod);
        final List<Mod> kids = mod.getJarJarMods();
        if (kids != null && !kids.isEmpty()) {
            for (Mod child : kids) traverseDepthFirst(child, visitor);
        }
    }

    private static void collectChains(String s, Set<String> out) {
        if (s == null || s.isEmpty()) return;
        final Matcher m = DOTTED_CHAIN.matcher(s);
        while (m.find()) out.add(m.group());
    }

    /**
     * Masks every '.' in the chain: "0.7.5.0.1" -> "0∙7∙5∙0∙1".
     */
    private static String maskAllDots(String chain) {
        return chain.replace('.', SAFE_DOT);
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
