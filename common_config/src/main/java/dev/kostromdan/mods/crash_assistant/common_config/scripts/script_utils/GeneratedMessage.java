package dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils;

import com.google.gson.Gson;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import org.apache.commons.jexl3.annotations.NoJexl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Script-provided additions to the copied support message. */
public final class GeneratedMessage {
    private static final Gson GSON = new Gson();
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern CUSTOM_LINE_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*\\$CUSTOM/([A-Za-z0-9_.-]+)\\$[\\t ]*(?:\\r?\\n|$)");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\$(CUSTOM/[A-Za-z0-9_.-]+|[A-Z][A-Z0-9_]*)\\$");
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparingInt((Entry entry) -> entry.priority).reversed()
            .thenComparing(entry -> entry.source)
            .thenComparing(entry -> entry.id);

    private static State state = new State();
    private static long revision;

    private GeneratedMessage() {
    }

    @NoJexl
    public static synchronized void putAnalysisResult(String id, String text) {
        putAnalysisResult(id, text, 0);
    }

    @NoJexl
    public static synchronized void putAnalysisResult(String id, String text, int priority) {
        putEntry(state.analysisResults, id, text, priority);
    }

    @NoJexl
    public static synchronized void removeAnalysisResult(String id) {
        removeEntry(state.analysisResults, id);
    }

    public static synchronized void putCustom(String slot, String id, String text) {
        putCustom(slot, id, text, 0);
    }

    public static synchronized void putCustom(String slot, String id, String text, int priority) {
        validateName("slot", slot);
        List<Entry> entries = state.custom.computeIfAbsent(slot, ignored -> new ArrayList<>());
        putEntry(entries, id, text, priority);
        if (entries.isEmpty()) {
            state.custom.remove(slot);
        }
    }

    public static synchronized void removeCustom(String slot, String id) {
        validateName("slot", slot);
        List<Entry> entries = state.custom.get(slot);
        if (entries != null) {
            removeEntry(entries, id);
            if (entries.isEmpty()) {
                state.custom.remove(slot);
            }
        }
    }

    public static synchronized void overrideStructure(String template) {
        String source = currentSource();
        if (template == null) {
            clearStructureOverride();
            return;
        }
        state.structureOverrides.remove(source);
        state.structureOverrides.put(source, template);
        revision++;
    }

    public static synchronized void clearStructureOverride() {
        if (state.structureOverrides.remove(currentSource()) != null) {
            revision++;
        }
    }

    public static synchronized String getCurrentStructure() {
        String override = getStructureOverride();
        return override == null
                ? CrashAssistantConfig.get("generated_message.message_structure", false)
                : override;
    }

    @NoJexl
    public static synchronized List<String> getAnalysisResults() {
        return orderedTexts(state.analysisResults);
    }

    @NoJexl
    public static synchronized String getStructureOverride() {
        String result = null;
        for (String template : state.structureOverrides.values()) {
            result = template;
        }
        return result;
    }

    @NoJexl
    public static synchronized long getRevision() {
        return revision;
    }

    @NoJexl
    public static synchronized boolean hasState() {
        return !state.analysisResults.isEmpty()
                || !state.custom.isEmpty()
                || !state.structureOverrides.isEmpty();
    }

    @NoJexl
    public static synchronized String exportState() {
        return GSON.toJson(state);
    }

    @NoJexl
    public static synchronized void importState(String json) {
        State imported = GSON.fromJson(json, State.class);
        state = imported == null ? new State() : imported;
        state.normalize();
        revision++;
    }

    @NoJexl
    public static synchronized void reset() {
        state = new State();
        revision++;
    }

    @NoJexl
    public static synchronized void clearContributionsForScript(String scriptName) {
        boolean changed = state.analysisResults.removeIf(entry -> scriptName.equals(entry.source));
        Iterator<Map.Entry<String, List<Entry>>> customIterator = state.custom.entrySet().iterator();
        while (customIterator.hasNext()) {
            List<Entry> entries = customIterator.next().getValue();
            changed |= entries.removeIf(entry -> scriptName.equals(entry.source));
            if (entries.isEmpty()) {
                customIterator.remove();
            }
        }
        changed |= state.structureOverrides.remove(scriptName) != null;
        if (changed) {
            revision++;
        }
    }

    @NoJexl
    public static String renderStructure(String template, Map<String, String> standardValues) {
        Map<String, String> customValues;
        synchronized (GeneratedMessage.class) {
            customValues = state.custom.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> String.join("\n", orderedTexts(entry.getValue())),
                    (left, right) -> right,
                    LinkedHashMap::new));
        }

        Matcher lineMatcher = CUSTOM_LINE_PATTERN.matcher(template);
        StringBuffer withoutEmptyCustomLines = new StringBuffer();
        while (lineMatcher.find()) {
            String value = customValues.get(lineMatcher.group(1));
            lineMatcher.appendReplacement(withoutEmptyCustomLines,
                    Matcher.quoteReplacement(value == null || value.isEmpty() ? "" : lineMatcher.group()));
        }
        lineMatcher.appendTail(withoutEmptyCustomLines);

        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(withoutEmptyCustomLines.toString());
        StringBuffer result = new StringBuffer();
        while (placeholderMatcher.find()) {
            String name = placeholderMatcher.group(1);
            String replacement;
            if (name.startsWith("CUSTOM/")) {
                replacement = customValues.getOrDefault(name.substring("CUSTOM/".length()), "");
            } else {
                replacement = standardValues.get(name);
            }
            placeholderMatcher.appendReplacement(result, Matcher.quoteReplacement(
                    replacement == null ? placeholderMatcher.group() : replacement));
        }
        placeholderMatcher.appendTail(result);
        return result.toString();
    }

    private static void putEntry(List<Entry> entries, String id, String text, int priority) {
        validateName("id", id);
        String source = currentSource();
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (source.equals(entry.source) && id.equals(entry.id)) {
                if (text == null || text.isEmpty()) {
                    iterator.remove();
                    revision++;
                } else if (!text.equals(entry.text) || priority != entry.priority) {
                    entry.text = text;
                    entry.priority = priority;
                    revision++;
                }
                return;
            }
        }
        if (text != null && !text.isEmpty()) {
            entries.add(new Entry(source, id, text, priority));
            revision++;
        }
    }

    private static void removeEntry(List<Entry> entries, String id) {
        validateName("id", id);
        String source = currentSource();
        if (entries.removeIf(entry -> source.equals(entry.source) && id.equals(entry.id))) {
            revision++;
        }
    }

    private static List<String> orderedTexts(List<Entry> entries) {
        return entries.stream()
                .sorted(ENTRY_ORDER)
                .map(entry -> entry.text)
                .collect(Collectors.toList());
    }

    private static String currentSource() {
        String source = ScriptUtils.getCurrentScriptName();
        return source == null ? "<unknown>" : source;
    }

    private static void validateName(String type, String value) {
        if (value == null || !NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(type + " must match " + NAME_PATTERN.pattern());
        }
    }

    private static final class Entry {
        private String source;
        private String id;
        private String text;
        private int priority;

        private Entry() {
        }

        private Entry(String source, String id, String text, int priority) {
            this.source = source;
            this.id = id;
            this.text = text;
            this.priority = priority;
        }
    }

    private static final class State {
        private List<Entry> analysisResults = new ArrayList<>();
        private Map<String, List<Entry>> custom = new LinkedHashMap<>();
        private Map<String, String> structureOverrides = new LinkedHashMap<>();

        private void normalize() {
            if (analysisResults == null) analysisResults = new ArrayList<>();
            if (custom == null) custom = new LinkedHashMap<>();
            if (structureOverrides == null) structureOverrides = new LinkedHashMap<>();
        }
    }
}
