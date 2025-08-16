package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogsList;
import dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.kostromdan.mods.crash_assistant.app.utils.ModuleFinder.SearchMode.CLASS_OR_PACKAGE;

public class MixinApply extends KnownCrashReason {
    public MixinApply() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.mixin_apply_common_start")
        );
    }

    private static final Pattern JSON_CONFIG_PATTERN = Pattern.compile("\\b(?![\\w.\\-]*refmap)[\\w.\\-]+\\.json\\b");


    @Override
    public boolean matches(Log latestLog) {
        String startWarn = "";
        if (!PlatformHelp.isLinkDefault()) {
            if (ModListDiff.isModpackCreator()) {
                startWarn = "<strong>You are seeing this analysis only because you are creator of this modpack. Won't be displayed to the end users.</strong>\n\n";
            } else {
                CrashAssistantApp.LOGGER.warn("Skipping MixinApply analysis due to it's in beta and game ran by the end user of this modpack.");
                return false; // Temporally disable for modpacks. todo: revert after out from beta.
            }
        }
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
        HashMap<String, String> configToJarMap = getMixinConfigToJarMapping(ModListUtils.getCurrentModList(true));
        for (Log log : logs) {
            MixinParsingResult result = parseLatestMixinError(log, configToJarMap);
            if (result != null) {
                String mixinConfig = result.getMixinConfig();
                String jarName = configToJarMap.get(mixinConfig);
                String conflictingJarName = null;
                String conflictingMixin = null;
                if (result.getRequiredJavaVersion() != null) {
                    message += LanguageProvider.get("warnings.mixin_apply_java_version");
                    message = message.replace("$REQUIRED_JAVA_VERSION$", "<strong style='color: red;'>" + result.getRequiredJavaVersion() + "</strong>");
                    message = message.replace("$CURRENT_JAVA_VERSION$", "<strong style='color: red;'>JAVA_" + getMajorJavaVersion() + "</strong>");
                } else if (result.isMissingOrCorruptedMixinConfig()){
                    message += LanguageProvider.get("warnings.mixin_config_missing_or_corrupted");
                }
                else {
                    if (result.getConflictingJarName() != null) {
                        conflictingJarName = result.getConflictingJarName();
                        message += LanguageProvider.get("warnings.mixin_apply_conflicting_with_jar");
                    } else {
                        conflictingMixin = findConflictingMixin(result.getMixinConfig(), latestLog, configToJarMap);
                        if (conflictingMixin != null) {
                            conflictingJarName = configToJarMap.get(conflictingMixin);
                            message += LanguageProvider.get("warnings.mixin_apply_conflicting");
                        } else {
                            message += LanguageProvider.get("warnings.mixin_apply");
                        }
                    }
                    message += LanguageProvider.get("warnings.mixin_apply_common_end");
                }
                message = message.replace("$MOD$", "<strong style='color: red;'>" + jarName + "</strong>");
                message = message.replace("$CONFIG$", "<strong>" + mixinConfig + "</strong>");
                if (conflictingMixin != null) {
                    message = message.replace("$CONFIG_2$", "<strong>" + conflictingMixin + "</strong>");
                }
                if (conflictingJarName != null) {
                    message = message.replace("$MOD_2$", "<strong style='color: red;'>" + conflictingJarName + "</strong>");
                }

                message = startWarn + message;
                return true;
            }
        }
        return false;
    }

    private static MixinParsingResult parseLatestMixinError(Log log, HashMap<String, String> configToJarMap) {
        List<String> lines = log.getType() == LogType.CRASH_REPORT ? log.getReader().getAllLinesList() : log.getReader().getLastNLines(300);
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.contains("org.spongepowered.asm.")) {
                if (!line.contains("Caused by:") && line.contains("org.spongepowered.asm.launch.MixinInitialisationError: Error initialising mixin config ")) {
                    HashSet<String> configs = extractFromLineMixinConfigs(line, configToJarMap);
                    if (configs.size() != 1) {
                        continue;
                    }
                    String config = configs.iterator().next();

                    for (int j = i + 1; j < lines.size(); j++) {
                        String nextLine = lines.get(j);
                        if (!nextLine.contains("Caused by: ") && !nextLine.contains("at ")) break;
                        if (!nextLine.contains("Caused by: ")) continue;
                        boolean isRequiredJavaVersion = nextLine.contains("java.lang.IllegalArgumentException: The requested compatibility level ") && nextLine.contains(" could not be set. Level is not supported by the active JRE or ASM version ");
                        boolean isMissingOrCorruptedMixinConfig = nextLine.contains("java.lang.IllegalArgumentException: The specified resource '") && nextLine.contains("' was invalid or could not be read");
                        if (!isRequiredJavaVersion && !isMissingOrCorruptedMixinConfig) {
                            break;
                        }
                        MixinParsingResult result = new MixinParsingResult(config, null);
                        if (isRequiredJavaVersion) {
                            String requiredJava = nextLine.split("Caused by: java\\.lang\\.IllegalArgumentException: The requested compatibility level ")[1].split(" ")[0];
                            result.setRequiredJavaVersion(requiredJava);
                        } else {
                            result.setMissingOrCorruptedMixinConfig(true);
                        }
                        return result;
                    }
                    continue;
                }


                if (!line.contains("Caused by: org.spongepowered.asm.mixin.")) continue;

                HashSet<String> configs = extractFromLineMixinConfigs(line, configToJarMap);
                if (configs.size() != 1) {
                    continue;
                }
                String[] patterns = {" merged by ", " was not located in the target class ", " previously written by "};
                for (String pattern : patterns) {
                    if (line.contains(pattern)) {
                        String packageName = line.split(pattern)[1].split(" ")[0];
                        if (isInternalClass(packageName)) continue;
                        List<String> jarsContainingModule = ModuleFinder.findJarsInFolderAsync(Collections.singletonList(packageName), ModListUtils.getCurrentModList(true), CLASS_OR_PACKAGE);
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
     * Recursively processes nested jars (jar-in-jar format).
     *
     * @param mods List of mods to process. If null, uses the current mod list.
     * @return HashMap mapping mixin config files to jar names
     */
    public static HashMap<String, String> getMixinConfigToJarMapping(LinkedHashSet<Mod> mods) {
        HashMap<String, String> result = new HashMap<>();

        Deque<Map.Entry<Mod, String>> stack = new ArrayDeque<>();
        for (Mod root : mods) {
            stack.push(new AbstractMap.SimpleEntry<>(root, root.getJarName()));
        }

        while (!stack.isEmpty()) {
            Map.Entry<Mod, String> entry = stack.pop();
            Mod mod = entry.getKey();
            String jarPath = entry.getValue();

            for (String cfg : mod.getMixinConfigs()) {
                result.putIfAbsent(cfg, jarPath);
            }

            for (Mod nested : mod.getJarJarMods()) {
                String fullNested = jarPath + "!" + nested.getPathFromJarJar() + nested.getJarName();
                stack.push(new AbstractMap.SimpleEntry<>(nested, fullNested));
            }
        }
        return result;
    }

    public static class MixinParsingResult {
        private final String mixinConfig;
        private final String conflictingJarName;
        private String requiredJavaVersion = null;
        private boolean missingOrCorruptedMixinConfig = false;

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

        public String getRequiredJavaVersion() {
            return requiredJavaVersion;
        }

        public void setRequiredJavaVersion(String requiredJavaVersion) {
            this.requiredJavaVersion = requiredJavaVersion;
        }

        public boolean isMissingOrCorruptedMixinConfig() {
            return missingOrCorruptedMixinConfig;
        }

        public void setMissingOrCorruptedMixinConfig(boolean missingOrCorruptedMixinConfig) {
            this.missingOrCorruptedMixinConfig = missingOrCorruptedMixinConfig;
        }
    }

    public static int getMajorJavaVersion() {
        String spec = System.getProperty("java.specification.version");
        String[] parts = spec.split("\\.");
        int major;
        if (parts[0].equals("1")) {
            major = Integer.parseInt(parts[1]);
        } else {
            major = Integer.parseInt(parts[0]);
        }
        return major;
    }
}
