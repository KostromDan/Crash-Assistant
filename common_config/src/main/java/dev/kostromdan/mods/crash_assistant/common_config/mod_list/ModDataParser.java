package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.gson.Gson;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ModDataParser {
    public static List<String> inJarPaths = PlatformHelp.getOrderedInJarPaths();
    private static final Path CACHE_FOLDER = Paths.get("local", "crash_assistant", "mod_data_cache_v2");
    private static final Gson GSON = new Gson();

    static {
        try {
            Files.createDirectories(CACHE_FOLDER);
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Failed to create cache directory", e);
        }
    }

    /**
     * Computes the cache file path from the JAR path.
     * For example, "mymod.jar" becomes "local/crash_assitant/mod_data_cache/mymod.mod_data.json".
     *
     * @param jarPath The path to the JAR file.
     * @return The path to the corresponding cache file.
     */
    private static Path getCacheFilePath(Path jarPath) {
        String jarName = jarPath.getFileName().toString();
        String baseName = jarName.endsWith(".jar") ? jarName.substring(0, jarName.length() - 4) : jarName;
        String cacheFileName = baseName + ".mod_data.json";
        return CACHE_FOLDER.resolve(cacheFileName);
    }

    /**
     * Retrieves the mod data from the cache.
     *
     * @param jarPath The path to the JAR file.
     * @return The cached Mod object, or null if it does not exist or cannot be read.
     */
    public static Mod getModFromCache(Path jarPath) {
        Path cacheFilePath = getCacheFilePath(jarPath);
        if (!Files.exists(cacheFilePath)) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(cacheFilePath.toFile(), "r");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {
            String json = new String(Files.readAllBytes(cacheFilePath), StandardCharsets.UTF_8);
            return GSON.fromJson(json, Mod.class);
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed to read or parse cache file for " + jarPath, e);
            return null;
        }
    }

    /**
     * Saves the mod data to the cache.
     * Does not save if modId or version is null.
     *
     * @param jarPath The path to the JAR file.
     * @param mod     The Mod object to save.
     */
    public static void saveModToCache(Path jarPath, Mod mod) {
        if (mod.getModId() == null || mod.getVersion() == null) {
            return;
        }
        Path cacheFilePath = getCacheFilePath(jarPath);
        try (RandomAccessFile raf = new RandomAccessFile(cacheFilePath.toFile(), "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
            channel.truncate(0);
            String json = GSON.toJson(mod);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Failed to save mod data to cache for " + jarPath, e);
        }
    }

    /**
     * Parses mod data from the JAR file, using the cache if available.
     *
     * @param jarPath The path to the JAR file.
     * @return The parsed Mod object, using the cache if possible.
     */
    public static Mod parseModData(Path jarPath) {
        Mod cachedMod = getModFromCache(jarPath);
        if (cachedMod != null) {
            return cachedMod;
        }
        Boolean isMCreator = null;
        List<String> mixinConfigs = new ArrayList<>();
        List<Mod> jarInJarPaths = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            isMCreator = jarFile.getJarEntry("net/mcreator") != null;

            jarFile.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".jar"))
                    .forEach(entry -> {
                        JarInJarHelper.LOGGER.info("Found jar in jar: " + entry.getName());
                        jarInJarPaths.add(new Mod(entry.getName().substring(entry.getName().lastIndexOf("/") + 1), null, null, null, new ArrayList<>(), new ArrayList<>()));
                    });


            for (String resourcePath : inJarPaths) {
                JarEntry entry = jarFile.getJarEntry(resourcePath);
                if (entry == null) continue;
                try {
                    Path temp = Files.createTempFile("moddata", resourcePath.substring(resourcePath.lastIndexOf('.') - 1));
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try (FileConfig config = FileConfig.builder(temp).build()) {
                        config.load();
                        Config mods;
                        String modId;
                        if (resourcePath.endsWith(".toml")) {
                            ArrayList<Object> modsList = config.get("mods");
                            if (modsList == null || modsList.isEmpty()) continue;
                            mods = (Config) modsList.get(0);
                            modId = mods.get("modId");
                        } else {
                            mods = config;
                            modId = mods.get("id");
                        }

                        ManifestParsingResult manifestParsingResult = null;

                        if (resourcePath.equals("META-INF/neoforge.mods.toml")) {
                            ArrayList<Object> mixinsList = config.get("mixins");
                            if (mixinsList != null) {
                                for (Object mixinObj : mixinsList) {
                                    if (mixinObj instanceof Config) {
                                        Config mixinConfig = (Config) mixinObj;
                                        String configPath = mixinConfig.get("config");
                                        if (configPath != null) {
                                            mixinConfigs.add(configPath);
                                        }
                                    }
                                }
                            }
                        } else {
                            manifestParsingResult = parseManifestFile(jarFile);
                            if (manifestParsingResult != null) {
                                mixinConfigs.addAll(manifestParsingResult.getMixinConfigs());
                            }
                        }

                        String version = mods.get("version");
                        if (Objects.equals(version, "${file.jarVersion}")) {
                            if (manifestParsingResult == null) manifestParsingResult = parseManifestFile(jarFile);
                            version = manifestParsingResult == null ? null : manifestParsingResult.getImplementationVersion();
                        } else if (Objects.equals(version, "${modVersion}")) {
                            version = null;
                        }
                        if (version == null && modId == null) {
                            throw new Exception("Failed to parse mod data(version AND modId) from " + resourcePath + " of " + jarPath.getFileName());
                        }
                        if (version == null)
                            JarInJarHelper.LOGGER.warn("Failed to parse version from " + resourcePath + " of " + jarPath.getFileName());
                        if (modId == null)
                            JarInJarHelper.LOGGER.warn("Failed to parse version from " + resourcePath + " of " + jarPath.getFileName());
                        Mod mod = new Mod(jarPath.getFileName().toString(), modId, version, isMCreator, mixinConfigs, jarInJarPaths);
                        saveModToCache(jarPath, mod);
                        return mod;
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                } catch (Exception e) {
                    JarInJarHelper.LOGGER.warn("Error while trying to parse " + resourcePath + " of " + jarPath.getFileName().toString() + ": ", e);
                }
            }
            if (jarPath.getFileName().toString().toLowerCase().contains("essential") && jarFile.getJarEntry("essential-loader.properties") != null) {
                return new Mod(jarPath.getFileName().toString(), "essential-container", null, isMCreator, mixinConfigs, jarInJarPaths);
            }
        } catch (Exception ignored) {
        }
        return new Mod(jarPath.getFileName().toString(), null, null, isMCreator, mixinConfigs, jarInJarPaths);
    }


    /**
     * Parses the version and mixin configs from the JAR's manifest file.
     *
     * @param jarFile The JAR file to parse.
     * @return The ManifestParsingResult containing version and mixin configs, or null if manifest not found.
     */
    private static ManifestParsingResult parseManifestFile(JarFile jarFile) {
        JarEntry manifestEntry = jarFile.getJarEntry("META-INF/MANIFEST.MF");
        if (manifestEntry == null) return null;
        try (InputStream is = jarFile.getInputStream(manifestEntry)) {
            Manifest manifest = new Manifest(is);
            Attributes mainAttributes = manifest.getMainAttributes();

            String implementationVersion = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);

            List<String> mixinConfigs = new ArrayList<>();
            String mixinConfigsValue = mainAttributes.getValue("MixinConfigs");
            if (mixinConfigsValue != null) {
                String[] configs = mixinConfigsValue.split(",");
                for (String config : configs) {
                    String trimmed = config.trim();
                    if (!trimmed.isEmpty()) {
                        mixinConfigs.add(trimmed);
                    }
                }
            }

            return new ManifestParsingResult(implementationVersion, mixinConfigs);
        } catch (Exception ignored) {
        }
        return null;
    }


    public static class ManifestParsingResult {
        private final String implementationVersion;
        private final List<String> mixinConfigs;

        public ManifestParsingResult(String implementationVersion, List<String> mixinConfigs) {
            this.implementationVersion = implementationVersion;
            this.mixinConfigs = mixinConfigs;
        }

        public String getImplementationVersion() {
            return implementationVersion;
        }

        public List<String> getMixinConfigs() {
            return mixinConfigs;
        }
    }

}