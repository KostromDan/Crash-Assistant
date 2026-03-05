package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.json.JsonFormat;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import org.apache.commons.jexl3.JexlContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class StartupScriptManager extends AbstractScriptManager {

    private static final StartupScriptManager INSTANCE = new StartupScriptManager();

    @Override
    protected Path getScriptsDir() {
        return Paths.get("config", "crash_assistant", "scripts", "startup");
    }

    @Override
    protected JexlContext createContext() {
        return super.createBaseContext();
    }

    public static void runStartupSequence(Path appJarPath, Path modJarPath) {
        migrateProblematicModsConfig();
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
            script.append("var allMods = ModListUtils.getCurrentModList(true);\n");
            script.append("var mods = allMods.stream().toMap(m -> m.modId, m -> m);\n\n");

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
                Path scriptPath = scriptsDir.resolve("00_migrated_problematic_mods.jexl");
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
}
