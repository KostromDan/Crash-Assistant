package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.GeneratedMessage;
import org.apache.commons.jexl3.JexlContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class StartupScriptManager extends AbstractScriptManager {

    private static final StartupScriptManager INSTANCE = new StartupScriptManager();
    private static final String MIGRATED_PROBLEMATIC_MODS_SCRIPT_NAME = "00_migrated_problematic_mods.jexl";
    private static final String EXAMPLE_STARTUP_SCRIPT_NAME = "example.jexl";
    private static final String LEGACY_MODS_MAP_LINE = "var mods = allMods.stream().toMap(m -> m.modId, m -> m);";
    private static final String FIXED_MODS_MAP_LINE = "var mods = ModListUtils.getCurrentModListMappedToModId();";
    private static final String LEGACY_HIGH_SYSTEM_LOAD_IF =
            "if (!exceedsTotalSystemCapacity && xmxBytes > 0 && stillNeeded > 0 && systemAvailable < stillNeeded) {";
    private static final String WINDOWS_ONLY_HIGH_SYSTEM_LOAD_IF =
            "if (PlatformHelp.isWindows() && !exceedsTotalSystemCapacity && xmxBytes > 0 && stillNeeded > 0 && systemAvailable < stillNeeded) {";

    @Override
    protected Path getScriptsDir() {
        return Paths.get("config", "crash_assistant", "scripts", "startup");
    }

    @Override
    protected JexlContext createContext() {
        return super.createBaseContext();
    }

    public static void runStartupSequence(Path appJarPath, Path modJarPath) {
        GeneratedMessage.reset();
        migrateProblematicModsConfig();
        patchLegacyMigratedProblematicModsScript();
        patchLegacyExampleStartupScript();
        INSTANCE.runScripts();
    }

    private static void migrateProblematicModsConfig() {
        Path configPath = Paths.get("config", "crash_assistant", "problematic_mods_config.json");
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            FileConfig config = FileConfig.builder(configPath, JsonFormat.fancyInstance())
                    .preserveInsertionOrder()
                    .build();
            config.load();

            if (config.valueMap().isEmpty() || (config.valueMap().size() == 1 && config.valueMap().containsKey("example_modid"))) {
                Files.deleteIfExists(configPath);
                return;
            }

            StringBuilder script = new StringBuilder();
            script.append(FIXED_MODS_MAP_LINE).append("\n\n");

            boolean scriptAdded = false;
            for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
                String modid = entry.getKey();
                if (modid.equals("example_modid")) continue;

                Object value = entry.getValue();
                if (value instanceof com.electronwill.nightconfig.core.Config) {
                    com.electronwill.nightconfig.core.Config modConfig = (com.electronwill.nightconfig.core.Config) value;
                    boolean shouldCrash = modConfig.getOrElse("should_crash_on_startup", false);
                    String msg = modConfig.getOrElse("msg", "");

                    msg = msg.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
                    
                    String varName = "m_" + modid.replaceAll("[^a-zA-Z0-9]", "_");
                    msg = msg.replace("$JAR_NAME$", "\" + " + varName + ".jarName + \"");

                    script.append("var ").append(varName).append(" = mods.get(\"").append(modid).append("\");\n");
                    script.append("if (").append(varName).append(" != null) {\n");

                    if (shouldCrash) {
                        script.append("    var warn = Startup.addCrashWarning(\"").append(msg).append("\");\n");
                        script.append("    warn.withModActions(").append(varName).append(");\n");
                        script.append("    Startup.markForCrash();\n");
                    } else {
                        script.append("    var warn = Startup.addBootWarning(\"").append(msg).append("\");\n");
                        script.append("    warn.withModActions(").append(varName).append(");\n");
                    }
                    script.append("}\n\n");
                    scriptAdded = true;
                }
            }

            if (scriptAdded) {
                Path scriptsDir = INSTANCE.getScriptsDir();
                Files.createDirectories(scriptsDir);
                Path scriptPath = scriptsDir.resolve(MIGRATED_PROBLEMATIC_MODS_SCRIPT_NAME);
                Files.write(scriptPath, script.toString().getBytes(StandardCharsets.UTF_8));
                JarInJarHelper.LOGGER.info("Migrated problematic_mods_config.json to {}", scriptPath);
            }

            Files.deleteIfExists(configPath);
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Failed to migrate problematic_mods_config.json", e);
            try {
                configPath.toFile().renameTo(configPath.getParent().resolve("problematic_mods_config.json.bak").toFile());
            } catch (Exception ignored) {
            }
        }
    }

    private static void patchLegacyMigratedProblematicModsScript() {
        Path scriptPath = INSTANCE.getScriptsDir().resolve(MIGRATED_PROBLEMATIC_MODS_SCRIPT_NAME);
        if (!Files.exists(scriptPath)) {
            return;
        }

        try {
            String script = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);
            String patchedScript = script
                    .replace(LEGACY_MODS_MAP_LINE, FIXED_MODS_MAP_LINE);
            if (patchedScript.equals(script)) {
                return;
            }

            Files.write(scriptPath, patchedScript.getBytes(StandardCharsets.UTF_8));
            JarInJarHelper.LOGGER.info("Patched legacy problematic mods migration script at {}", scriptPath);
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed to patch legacy problematic mods migration script at {}", scriptPath, e);
        }
    }

    private static void patchLegacyExampleStartupScript() {
        Path scriptPath = INSTANCE.getScriptsDir().resolve(EXAMPLE_STARTUP_SCRIPT_NAME);
        if (!Files.exists(scriptPath)) {
            return;
        }

        try {
            String script = new String(Files.readAllBytes(scriptPath), StandardCharsets.UTF_8);
            String patchedScript = script.replace(LEGACY_HIGH_SYSTEM_LOAD_IF, WINDOWS_ONLY_HIGH_SYSTEM_LOAD_IF);
            if (patchedScript.equals(script)) {
                return;
            }

            Files.write(scriptPath, patchedScript.getBytes(StandardCharsets.UTF_8));
            JarInJarHelper.LOGGER.info("Patched legacy startup example script at {}", scriptPath);
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed to patch legacy startup example script at {}", scriptPath, e);
        }
    }
}
