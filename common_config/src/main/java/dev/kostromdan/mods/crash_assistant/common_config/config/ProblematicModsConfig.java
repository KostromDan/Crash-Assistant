package dev.kostromdan.mods.crash_assistant.common_config.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.json.JsonFormat;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
        FileConfig config = FileConfig.builder(CONFIG_PATH, JsonFormat.fancyInstance())
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
        LinkedHashSet<Mod> currentMods = ModListUtils.getCurrentModList(true);

        Map<String, ProblematicMod> configMap = problematicMods.stream()
                .collect(Collectors.toMap(ProblematicMod::getModid, pm -> pm));

        return currentMods.stream()
                .filter(mod -> configMap.containsKey(mod.getModId()))
                .map(mod -> {
                    ProblematicMod fromConfig = configMap.get(mod.getModId());
                    return new ProblematicMod(
                            fromConfig.getModid(),
                            mod,
                            fromConfig.isShouldCrashOnStartup(),
                            fromConfig.getMsg()
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
        if (problematicModsFromConfig.stream().noneMatch(ProblematicMod::isShouldCrashOnStartup)) return;

        List<ProblematicMod> problematicMods = getCurrentProblematicMods();
        boolean shouldCrash = false;
        for (ProblematicMod mod : problematicMods) {
            if (mod.isShouldCrashOnStartup()) {
                shouldCrash = true;
                Mod currentMod = mod.getCurrentMod();
                JarInJarHelper.LOGGER.error("Detected " + currentMod.getJarName() + "(modId: " + mod.getModid() + ") in current modlist.\nIt marked as incompatible with this modpack(" + CONFIG_PATH + ").\nCrashing game and starting Crash Assistant.\nMessage from modpack creators:\n" + mod.getMsg());
            }
        }
        if (shouldCrash) {
            ProcessHelper.exitProcess(-1);
        }
    }

    /**
     * Class representing a problematic mod with its configuration.
     */
    public static class ProblematicMod {
        private final String modid;
        private final Mod currentMod;
        private final boolean shouldCrashOnStartup;
        private final String msg;

        public ProblematicMod(
                String modid,
                Mod currentMod,
                boolean shouldCrashOnStartup,
                String msg
        ) {
            this.modid = modid;
            this.currentMod = currentMod;
            this.shouldCrashOnStartup = shouldCrashOnStartup;
            this.msg = msg;
        }

        public String getModid() {
            return modid;
        }

        public Mod getCurrentMod() {
            return currentMod;
        }

        public boolean isShouldCrashOnStartup() {
            return shouldCrashOnStartup;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProblematicMod that = (ProblematicMod) o;
            return shouldCrashOnStartup == that.shouldCrashOnStartup &&
                    Objects.equals(modid, that.modid) &&
                    Objects.equals(currentMod, that.currentMod) &&
                    Objects.equals(msg, that.msg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modid, currentMod, shouldCrashOnStartup, msg);
        }

        @Override
        public String toString() {
            return "ProblematicMod{" +
                    "modid='" + modid + '\'' +
                    ", currentMod=" + currentMod +
                    ", shouldCrashOnStartup=" + shouldCrashOnStartup +
                    ", msg='" + msg + '\'' +
                    '}';
        }
    }
}
