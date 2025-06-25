package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogsList;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MixinApply extends KnownCrashReason {
    public MixinApply() {
        super(
                LogType.LOG,
                ""
        );
    }

    private static final Pattern JSON_CONFIG_PATTERN = Pattern.compile("\\b(?![\\w.\\-]*refmap)[\\w.\\-]+\\.json\\b");


    @Override
    public boolean matches(Log latestLog) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        List<Log> logs = new ArrayList<>();
        for (Log log : LogsList.getLogs()) {
            if (log.getType() == LogType.LAUNCHER_LOG) {
                logs.add(log);
            }
        }
        for (Log log : LogsList.getLogs()) {
            if (log.getType() == LogType.CRASH_REPORT) {
                logs.add(log);
            }
        }
        logs.add(latestLog);
        HashMap<String, String> configToJarMap = getMixinConfigToJarMapping();
        for (Log log : logs) {
            MixinParsingResult result = parseLatestMixinError(log, configToJarMap);
            if (result != null) {
                String mixinConfig = result.getMixinConfig();
                String jarName = configToJarMap.get(mixinConfig);
                String conflictingJarName = null;
                String conflictingMixin = null;
                if (result.getConflictingJarName() != null) {
                    conflictingJarName = result.getConflictingJarName();
                    message = LanguageProvider.get("warnings.mixin_apply_conflicting_with_jar");
                } else {
                    conflictingMixin = findConflictingMixin(result.getMixinConfig(), latestLog, configToJarMap);
                    if (conflictingMixin != null) {
                        conflictingJarName = configToJarMap.get(conflictingMixin);
                        message = LanguageProvider.get("warnings.mixin_apply_conflicting");
                    } else {
                        message = LanguageProvider.get("warnings.mixin_apply");
                    }
                }
                message = message.replace("$MOD$", "<strong style='color: red;'>" + jarName + "</strong>");
                message = message.replace("$CONFIG$", "<strong>" + mixinConfig + "</strong>");
                if (conflictingMixin != null) {
                    message = message.replace("$CONFIG_2$", "<strong>" + conflictingMixin + "</strong>");
                }
                if (conflictingJarName != null) {
                    message = message.replace("$MOD_2$", "<strong style='color: red;'>" + conflictingJarName + "</strong>");
                }

                return true;
            }
        }
        return false;
    }

    private static MixinParsingResult parseLatestMixinError(Log log, HashMap<String, String> configToJarMap) {
        List<String> lines = log.getType() == LogType.CRASH_REPORT ? log.getReader().getAllLinesList() : log.getReader().getLastNLines(1000);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.contains("Caused by: org.spongepowered.asm.mixin.")) {
                HashSet<String> configs = extractFromLineMixinConfigs(line, configToJarMap);
                if (configs.size() != 1) {
                    continue;
                }
                String[] patterns = {" merged by ", " was not located in the target class ", " previously written by "};
                for (String pattern : patterns) {
                    if (line.contains(pattern)) {
                        String packageName = line.split(pattern)[1].split(" ")[0];
                        if (isInternalClass(packageName)) continue;
                        List<String> jarsContainingModule = ModuleFinder.findJarsInFolderAsync(Collections.singletonList(packageName), ModListUtils.getCurrentModList(true));
                        if (jarsContainingModule.isEmpty()) continue;
                        return new MixinParsingResult(configs.iterator().next(), jarsContainingModule.get(0));
                    }
                }
                return new MixinParsingResult(configs.iterator().next(), null);

            }
        }
        return null;
    }

    private static String findConflictingMixin(String mixinConfig, Log log, HashMap<String, String> configToJarMap) {
        List<String> lines = log.getReader().getLastNLines(1000);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (!line.contains(mixinConfig)) continue;
            HashSet<String> configs = extractFromLineMixinConfigs(line, configToJarMap);
            if (configs.size() != 2) continue;
            if (line.contains(" conflict. Skipping ")) {
                configs.remove(mixinConfig);
                return configs.iterator().next();
            }
        }
        return null;
    }

    /**
     * Scans a single log line and extracts all unique .json names
     * (excluding those containing "refmap").
     *
     * @param line one line from your log
     * @return set of distinct config names
     */
    public static HashSet<String> extractFromLineMixinConfigs(String line, HashMap<String, String> configToJarMap) {
        HashSet<String> configs = new HashSet<>();
        Matcher m = JSON_CONFIG_PATTERN.matcher(line);
        while (m.find()) {
            String cfg = m.group();
            if (configToJarMap.containsKey(cfg)) configs.add(cfg);
        }
        return configs;
    }

    public static boolean isInternalClass(String className) {
        className = ModuleFinder.normalizeModuleName(className);
        if (className.startsWith("net/minecraft/")) return true;
        return false;
    }

    /**
     * Gets a mapping of mixin config files to the jar names that contain them.
     * Uses the current mod list.
     *
     * @return HashMap mapping mixin config files to jar names
     */
    public static HashMap<String, String> getMixinConfigToJarMapping() {
        return getMixinConfigToJarMapping(null);
    }

    /**
     * Gets a mapping of mixin config files to the jar names that contain them.
     * Recursively processes nested jars (jar-in-jar format).
     *
     * @param mods List of mods to process. If null, uses the current mod list.
     * @return HashMap mapping mixin config files to jar names
     */
    public static HashMap<String, String> getMixinConfigToJarMapping(LinkedHashSet<Mod> mods) {
        HashMap<String, String> configToJarMap = new HashMap<>();

        if (mods == null) {
            mods = ModListUtils.getCurrentModList(true);
        }

        for (Mod mod : mods) {
            // Process the mod's mixin configs
            HashSet<String> mixinConfigs = mod.getMixinConfigs();
            if (mixinConfigs != null && !mixinConfigs.isEmpty()) {
                String jarName = mod.getJarName();
                String pathFromJarJar = mod.getPathFromJarJar();

                // Build the full jar path if it's a nested jar
                String fullJarPath = jarName;
                if (pathFromJarJar != null && !pathFromJarJar.isEmpty()) {
                    fullJarPath = pathFromJarJar + "!/" + jarName;
                }

                // Add each mixin config to the map
                for (String mixinConfig : mixinConfigs) {
                    configToJarMap.put(mixinConfig, fullJarPath);
                }
            }

            // Recursively process nested jars
            List<Mod> jarJarMods = mod.getJarJarMods();
            if (jarJarMods != null && !jarJarMods.isEmpty()) {
                LinkedHashSet<Mod> nestedMods = new LinkedHashSet<>(jarJarMods);
                HashMap<String, String> nestedMap = getMixinConfigToJarMapping(nestedMods);
                configToJarMap.putAll(nestedMap);
            }
        }

        return configToJarMap;
    }


    public static class MixinParsingResult {
        private final String mixinConfig;
        private final String conflictingJarName;

        public MixinParsingResult(String mixinConfig, String conflictingJarName) {
            this.mixinConfig = mixinConfig;
            this.conflictingJarName = conflictingJarName;
        }

        public String getMixinConfig() {
            return mixinConfig;
        }

        public String getConflictingJarName() {
            return conflictingJarName;
        }
    }


//    private static final Pattern AT_PATTERN = Pattern.compile("^\\s*at\\s+.*");
//    private static final Pattern MORE_PATTERN = Pattern.compile("^\\s*\\.{3}\\s+\\d+\\s+more$");
//    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile("^\\s*Caused\\s+by:.*");
//
//    private static boolean isStackTraceLine(String line) {
//        if (line == null || line.trim().isEmpty()) {
//            return false;
//        }
//
//        return AT_PATTERN.matcher(line).matches() ||
//                MORE_PATTERN.matcher(line).matches() ||
//                CAUSED_BY_PATTERN.matcher(line).matches();
//    }
}
