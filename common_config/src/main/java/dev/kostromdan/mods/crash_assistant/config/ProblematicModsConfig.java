package dev.kostromdan.mods.crash_assistant.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProblematicModsConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "crash_assistant", "problematic_mods_config.json");

    /**
     * Loads and returns a list of problematic mod configurations from the JSON config file.
     *
     * @return List of ProblematicMod records representing problematic mods
     */
    public static List<ProblematicMod> getProblematicModsFromConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
        } catch (Exception ignored) {
        }
        // Build and load the configuration file
        FileConfig config = FileConfig.builder(CONFIG_PATH)
                .preserveInsertionOrder()
                .build();
        try {
            config.load();
        } catch (ParsingException e) {
            JarInJarHelper.LOGGER.error("Error while loading problematic_mods_config.json, saved old problematic config as 'problematic_mods_config.json.bak', resetting config to default values:", e);
            try {
                CONFIG_PATH.toFile().renameTo(Paths.get(CONFIG_PATH.getParent().toString(), "problematic_mods_config.json.bak").toFile());
            } catch (Exception e1) {
                JarInJarHelper.LOGGER.error("Failed to rename 'problematic_mods_config.json' to 'problematic_mods_config.json.bak': ", e1);
            }
            config.clear();
        }

        // Ensure the config has default values if empty
        setUpDefaultConfig(config);
        config.save();

        // Read the config and build the list of ProblematicMod records
        List<ProblematicMod> problematicMods = new ArrayList<>();
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            String modid = entry.getKey();
            if (modid.equals("example_modid")) continue;
            Object value = entry.getValue();
            if (value instanceof com.electronwill.nightconfig.core.Config) {
                com.electronwill.nightconfig.core.Config modConfig = (com.electronwill.nightconfig.core.Config) value;
                boolean shouldCrash = modConfig.getOrElse("should_crash_on_startup", false);
                boolean displayButtons = modConfig.getOrElse("display_remove_disable_buttons", false);
                String msg = modConfig.getOrElse("msg", "");
                problematicMods.add(new ProblematicMod(modid, null, shouldCrash, msg));
            } else {
                JarInJarHelper.LOGGER.warn("Invalid config entry for modid '{}': expected a config object, got {}", modid, value);
            }
        }
        return problematicMods;
    }

    /**
     * Returns a list of problematic mods that are currently present in the mod list,
     *
     * @return List of ProblematicMod instances for mods currently loaded
     */
    public static List<ProblematicMod> getCurrentProblematicMods() {
        List<ProblematicMod> problematicMods = ProblematicModsConfig.getProblematicModsFromConfig();
        LinkedHashSet<Mod> currentMods = ModListUtils.getCurrentModList();

        Map<String, ProblematicMod> configMap = problematicMods.stream()
                .collect(Collectors.toMap(ProblematicMod::modid, pm -> pm));

        return currentMods.stream()
                .filter(mod -> configMap.containsKey(mod.getModId()))
                .map(mod -> {
                    ProblematicMod fromConfig = configMap.get(mod.getModId());
                    return new ProblematicMod(
                            fromConfig.modid(),
                            mod,
                            fromConfig.should_crash_on_startup(),
                            fromConfig.msg()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Sets up the default configuration if the config is empty.
     * Adds an example entry to guide users on the expected format.
     *
     * @param config The CommentedFileConfig to set up
     */
    public static void setUpDefaultConfig(FileConfig config) {
        if (config.isEmpty()) {
            // Create an example configuration entry
            com.electronwill.nightconfig.core.Config exampleModConfig = com.electronwill.nightconfig.core.Config.inMemory();
            exampleModConfig.set("should_crash_on_startup", true);
            exampleModConfig.set("msg", "Custom msg on crash for this mod id. You can use $JAR_NAME$ placeholder, which will be replaced with jar name. You can use HTML here, it will work.");
            config.set("example_modid", exampleModConfig);
        }
    }

    public static void crashIfProblematicMod() {
        List<ProblematicMod> problematicModsFromConfig = getProblematicModsFromConfig();
        if (problematicModsFromConfig.stream().noneMatch(ProblematicMod::should_crash_on_startup)) return;

        List<ProblematicMod> problematicMods = getCurrentProblematicMods();
        boolean shouldCrash = false;
        for (ProblematicMod mod : problematicMods) {
            if (mod.should_crash_on_startup()) {
                shouldCrash = true;
                Mod currentMod = mod.currentMod();
                JarInJarHelper.LOGGER.error("Detected " + currentMod.getJarName() + "(modId: " + mod.modid() + ") in current modlist. It marked as incompatible with this modpack(" + CONFIG_PATH + "). Crashing game and starting Crash Assistant.");
            }
        }
        if (shouldCrash) {
            System.exit(-1);
        }
    }

    /**
     * Record representing a problematic mod with its configuration.
     */
    public record ProblematicMod(
            String modid,
            Mod currentMod,
            boolean should_crash_on_startup,
            String msg
    ) {
    }
}