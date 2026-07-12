package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import org.apache.commons.jexl3.annotations.NoJexl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ModListUtils {
    public static final Logger LOGGER = LogManager.getLogger();
    @NoJexl
    public static Path MODS_FOLDER = Paths.get("mods");
    private static final Path RESOURCEPACKS_FOLDER = Paths.get("resourcepacks");
    private static final Path DATAPACKS_FOLDER = Paths.get("datapacks");
    private static final Path JSON_FILE = Paths.get("config", "crash_assistant", "modlist.json");
    public static String currentUsername = "";
    private static LinkedHashSet<Mod> cachedModList = null;


    public synchronized static LinkedHashSet<Mod> getCurrentModList(boolean useCache) {
        if (cachedModList != null && useCache) {
            return cachedModList;
        }
        try {
            LinkedHashSet<Mod> currentMods = new LinkedHashSet<>();

            if (CrashAssistantConfig.getBoolean("modpack_modlist.add_modloader_jar_name")) {
                currentMods.add(new Mod(
                        PlatformHelp.loaderJarName + " (modloader)",
                        PlatformHelp.platform.name().toLowerCase(),
                        PlatformHelp.platform.name().toLowerCase(),
                        PlatformHelp.loaderJarName,
                        null, null, new HashSet<>(), new ArrayList<>(), null,
                        null, null)
                );
            }

            if (Files.exists(MODS_FOLDER)) {
                long start = System.currentTimeMillis();

                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                List<Future<Mod>> futures = new ArrayList<>();

                Files.list(MODS_FOLDER)
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                        .sorted(new PathComparator())
                        .forEach(path -> futures.add(executor.submit(() -> ModDataParser.parseModData(path))));

                for (Future<Mod> future : futures) {
                    currentMods.add(future.get());
                }

                executor.shutdown();
                LOGGER.info("Parsed " + currentMods.size() + " mod(s) metadata in " + (System.currentTimeMillis() - start) + " ms");
            }
            if (Files.exists(RESOURCEPACKS_FOLDER) && CrashAssistantConfig.getBoolean("modpack_modlist.add_resourcepacks")) {
                Files.list(RESOURCEPACKS_FOLDER).sorted(new PathComparator()).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (Files.isDirectory(path) || filename.endsWith(".zip")) {
                        currentMods.add(new Mod(filename + " (resourcepack)"));
                    }
                });
            }
            if (Files.exists(DATAPACKS_FOLDER) && CrashAssistantConfig.getBoolean("modpack_modlist.add_datapacks")) {
                Files.list(DATAPACKS_FOLDER).sorted(new PathComparator()).forEach(path -> {
                    String filename = path.getFileName().toString();
                    if (Files.isDirectory(path) || filename.endsWith(".zip")) {
                        currentMods.add(new Mod(filename + " (datapack)"));
                    }
                });
            }
            if (useCache) {
                cachedModList = currentMods;
            }
            return currentMods;
        } catch (Exception e) {
            LOGGER.error("Error while getting current mod list: ", e);
        }
        return new LinkedHashSet<>();
    }

    public static Map<String, Mod> getCurrentModListMappedToModId() {
        return getCurrentModListMappedToModId(false);
    }

    public static Map<String, Mod> getCurrentModListMappedToModId(boolean includeJarInJarEntries) {
        LinkedHashMap<String, Mod> modsById = new LinkedHashMap<>();
        LinkedHashSet<Mod> currentMods = getCurrentModList(true);

        if (includeJarInJarEntries) {
            forEachModRecursive(currentMods, mod -> putIfAbsentByModId(modsById, mod));
        } else {
            for (Mod mod : currentMods) {
                putIfAbsentByModId(modsById, mod);
            }
        }

        return modsById;
    }

    public static LinkedHashSet<Mod> getSavedModList() {
        try {
            if (Files.exists(JSON_FILE)) {
                String json = new String(Files.readAllBytes(JSON_FILE));
                return Mod.GSON.fromJson(json, Mod.TYPE);

            }
        } catch (Exception e) {
            LOGGER.error("Error while getting Modlist", e);

        }
        return new LinkedHashSet<>();
    }

    @NoJexl
    public static void forEachModRecursive(Collection<Mod> mods, Consumer<Mod> consumer) {
        if (mods == null || consumer == null) {
            return;
        }
        for (Mod mod : mods) {
            forEachModRecursive(mod, consumer);
        }
    }

    @NoJexl
    public static void saveCurrentModList() {
        try {
            try (FileWriter writer = new FileWriter(JSON_FILE.toFile())) {
                String jsonOutput = Mod.GSON.toJson(getCurrentModList(false), Mod.TYPE);
                writer.write(jsonOutput);
            }

            LOGGER.info("Modlist saved to " + JSON_FILE);
        } catch (Exception e) {
            LOGGER.error("Error while saving Modlist", e);
        }
    }

    public static String getCurrentUsername() {
        if (currentUsername.isEmpty()) {
            Optional<String> x = ProcessSignalIO.getInfo("username");
            x.ifPresent(s -> currentUsername = s);
        }
        return currentUsername;
    }

    @NoJexl
    private static void forEachModRecursive(Mod mod, Consumer<Mod> consumer) {
        if (mod == null) {
            return;
        }

        consumer.accept(mod);
        List<Mod> children = mod.getJarJarMods();
        if (children == null || children.isEmpty()) {
            return;
        }

        for (Mod child : children) {
            forEachModRecursive(child, consumer);
        }
    }

    @NoJexl
    private static void putIfAbsentByModId(Map<String, Mod> modsById, Mod mod) {
        if (mod == null || mod.getModId() == null) {
            return;
        }
        modsById.putIfAbsent(mod.getModId(), mod);
    }
}
