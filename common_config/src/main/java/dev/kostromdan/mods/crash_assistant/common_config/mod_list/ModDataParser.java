package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.json.JsonParser;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;

public class ModDataParser {
    public static final List<String> inJarPaths = PlatformHelp.getOrderedInJarPaths();
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
        return CACHE_FOLDER.resolve(baseName + ".mod_data.json");
    }

    /**
     * Retrieves the mod data from the cache.
     *
     * @param jarPath The path to the JAR file.
     * @return The cached Mod object, or null if it does not exist or cannot be read.
     */
    public static Mod getModFromCache(Path jarPath) {
        Path cacheFilePath = getCacheFilePath(jarPath);
        if (!Files.exists(cacheFilePath)) return null;

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
        if (mod.getModId() == null || mod.getVersion() == null) return;

        Path cacheFilePath = getCacheFilePath(jarPath);
        try (RandomAccessFile raf = new RandomAccessFile(cacheFilePath.toFile(), "rw");
             FileChannel ch = raf.getChannel();
             FileLock ignored = ch.lock()) {

            ch.truncate(0);
            ch.write(ByteBuffer.wrap(GSON.toJson(mod).getBytes(StandardCharsets.UTF_8)));
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
        Mod cached = getModFromCache(jarPath);
        if (cached != null) return cached;

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Mod mod = parseJarFile(jarFile, jarPath.getFileName().toString(), null);
            saveModToCache(jarPath, mod);
            return mod;
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed to parse " + jarPath.getFileName() + ": ", e);
            return new Mod(jarPath.getFileName().toString(), null, null, null,
                    new HashSet<>(), new ArrayList<>(), null);
        }
    }

    private static Mod parseJarFile(JarFile jarFile, String currentJarName, String jarJarPath) {
        Boolean isMCreator = null;
        boolean hasEssentialLoader = false;

        HashSet<String> mixinConfigs = new HashSet<>();
        List<Mod> jarInJarMods = new ArrayList<>();

        try {
            if (jarFile.getEntry("net/mcreator/") != null) {
                isMCreator = true;
            }

            if (jarFile.getEntry("essential-loader.properties") != null) {
                hasEssentialLoader = true;
            }

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) continue;

                if (name.startsWith("META-INF/") && name.endsWith(".jar")) {
                    processNestedJar(name, () -> readEntryBytes(jarFile, entry), jarInJarMods);
                    continue;
                }

                if (name.endsWith(".json")) {
                    if (name.substring(name.lastIndexOf('/') + 1).contains("mixin") && !name.startsWith("data/")) {
                        mixinConfigs.add(name);
                        continue;
                    }
                }
            }

            Map<String, byte[]> descriptorBytes = new HashMap<>();
            Manifest manifest = null;

            for (String descriptorPath : inJarPaths) {
                JarEntry entry = jarFile.getJarEntry(descriptorPath);
                if (entry != null) {
                    descriptorBytes.put(descriptorPath, readEntryBytes(jarFile, entry));
                }
            }

            // Get manifest if needed
            ManifestProvider manifestProvider = () -> {
                if (manifest == null) {
                    return jarFile.getManifest();
                }
                return manifest;
            };

            return parseDescriptorsAndBuildMod(descriptorBytes, manifestProvider, currentJarName,
                    jarJarPath, isMCreator, hasEssentialLoader, mixinConfigs, jarInJarMods);

        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed while processing " + currentJarName, e);
        }

        return new Mod(currentJarName, null, null,
                isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
    }

    // Overloaded method for parsing from JarInputStream (for nested jars)
    private static Mod parseJarFile(JarInputStream jis, String currentJarName, String jarJarPath) {
        Boolean isMCreator = null;
        boolean hasEssentialLoader = false;

        HashSet<String> mixinConfigs = new HashSet<>();
        List<Mod> jarInJarMods = new ArrayList<>();
        Map<String, byte[]> descriptorBytes = new HashMap<>();

        try {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();

                if (isMCreator == null && name.startsWith("net/mcreator")) {
                    isMCreator = true;
                }

                if ("essential-loader.properties".equals(name)) {
                    hasEssentialLoader = true;
                }

                if (name.startsWith("META-INF/") && name.endsWith(".jar")) {
                    processNestedJar(name, () -> readEntryBytes(jis), jarInJarMods);
                    continue;
                }

                if (inJarPaths.contains(name)) {
                    descriptorBytes.put(name, readEntryBytes(jis));
                    continue;
                }

                if (name.endsWith(".json")) {
                    if (name.substring(name.lastIndexOf('/') + 1).contains("mixin") && !name.startsWith("data/")) {
                        mixinConfigs.add(name);
                        continue;
                    }
                }
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed while streaming entries of " + currentJarName, e);
        }

        ManifestProvider manifestProvider = () -> jis.getManifest();

        return parseDescriptorsAndBuildMod(descriptorBytes, manifestProvider, currentJarName,
                jarJarPath, isMCreator, hasEssentialLoader, mixinConfigs, jarInJarMods);
    }

    // Functional interface for lazy byte loading
    private interface ByteSupplier {
        byte[] get() throws Exception;
    }

    // Functional interface for manifest provider
    private interface ManifestProvider {
        Manifest get() throws IOException;
    }

    private static void processNestedJar(String name, ByteSupplier byteSupplier, List<Mod> jarInJarMods) {
        String nestedJarName = name.substring(name.lastIndexOf('/') + 1);
        String normalizedNestedJarName = nestedJarName.toLowerCase();

        if (normalizedNestedJarName.contains("mixinextras") || normalizedNestedJarName.contains("mixinsquared")) {
            return;
        }

        String nestedJarPath = "/" + (name.contains("/") ? name.substring(0, name.lastIndexOf('/') + 1) : "");

        try {
            byte[] nestedBytes = byteSupplier.get();
            try (JarInputStream nestedJis = new JarInputStream(new ByteArrayInputStream(nestedBytes))) {
                Mod nested = parseJarFile(nestedJis, nestedJarName, nestedJarPath);
                nested = new Mod(nestedJarName, nested.getModId(), nested.getVersion(),
                        nested.IsMCreator(), nested.getMixinConfigs(),
                        nested.getJarJarMods(), nestedJarPath);
                jarInJarMods.add(nested);
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Error processing nested jar " + name + ": " + e.getMessage());
            jarInJarMods.add(new Mod(nestedJarName, null, null, null,
                    new HashSet<>(), new ArrayList<>(), nestedJarPath));
        }
    }

    private static Mod parseDescriptorsAndBuildMod(Map<String, byte[]> descriptorBytes, ManifestProvider manifestProvider, String currentJarName, String jarJarPath, Boolean isMCreator, boolean hasEssentialLoader, HashSet<String> mixinConfigs, List<Mod> jarInJarMods) {
        for (String descriptorPath : inJarPaths) {
            byte[] bytes = descriptorBytes.get(descriptorPath);
            if (bytes == null) continue;

            try {
                Config cfg = loadConfigFromBytes(currentJarName + "/" + descriptorPath, bytes);
                if (cfg == null) continue;

                ParsedModInfo modInfo = parseModConfig(cfg, descriptorPath, manifestProvider, mixinConfigs);
                if (modInfo == null) continue;

                if (modInfo.version == null && modInfo.modId == null) {
                    throw new Exception("Failed to parse mod data (version AND modId) from " +
                            descriptorPath + " of " + currentJarName);
                }
                if (modInfo.version == null) {
                    JarInJarHelper.LOGGER.warn("Failed to parse version from " +
                            descriptorPath + " of " + currentJarName);
                }
                if (modInfo.modId == null) {
                    JarInJarHelper.LOGGER.warn("Failed to parse modId from " +
                            descriptorPath + " of " + currentJarName);
                }

                return new Mod(currentJarName, modInfo.modId, modInfo.version,
                        isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
            } catch (Exception e) {
                JarInJarHelper.LOGGER.warn("Error parsing " + descriptorPath + " of " +
                        currentJarName + ": ", e);
            }
        }

        // Special-case Essential
        if (currentJarName.toLowerCase().contains("essential") && hasEssentialLoader) {
            return new Mod(currentJarName, "essential-container", null,
                    isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
        }

        // Nothing found
        return new Mod(currentJarName, null, null,
                isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
    }

    private static class ParsedModInfo {
        final String modId;
        final String version;

        ParsedModInfo(String modId, String version) {
            this.modId = modId;
            this.version = version;
        }
    }

    private static ParsedModInfo parseModConfig(Config cfg, String descriptorPath, ManifestProvider manifestProvider, HashSet<String> mixinConfigs) throws IOException {
        Config mods;
        String modId;
        String version;
        ManifestParsingResult mp = null;

        if (descriptorPath.endsWith(".toml")) {
            List<Object> modsList = cfg.get("mods");
            if (modsList == null || modsList.isEmpty()) return null;

            mods = (Config) modsList.get(0);
            modId = mods.get("modId");

            if ("META-INF/neoforge.mods.toml".equals(descriptorPath)) {
                List<Object> mixinsList = cfg.get("mixins");
                if (mixinsList != null) {
                    for (Object obj : mixinsList) {
                        if (obj instanceof Config) {
                            String c = ((Config) obj).get("config");
                            if (c != null) mixinConfigs.add(c);
                        }
                    }
                }
            } else {
                mp = parseManifest(manifestProvider.get());
                if (mp != null) mixinConfigs.addAll(mp.getMixinConfigs());
            }
        } else if (descriptorPath.endsWith(".json")) {
            mods = cfg;
            modId = mods.get("id");

            Object mixinsObj = mods.get("mixins");
            if (mixinsObj instanceof List) {
                mixinConfigs.addAll(
                        ((List<?>) mixinsObj).stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .collect(Collectors.toList()));
            }
        } else if (descriptorPath.endsWith(".info")) {
            List<Object> modsList = cfg.get("mods");
            if (modsList == null || modsList.isEmpty()) return null;

            mods = (Config) modsList.get(0);
            modId = mods.get("modid");
        } else {
            throw new IllegalArgumentException("Unsupported descriptor file extension: " + descriptorPath);
        }

        version = mods.get("version");
        if (Objects.equals(version, "${file.jarVersion}")) {
            if (mp == null) mp = parseManifest(manifestProvider.get());
            version = mp == null ? null : mp.getImplementationVersion();
        } else if (Objects.equals(version, "${modVersion}")) {
            version = null;
        }

        return new ParsedModInfo(modId, version);
    }

    private static byte[] readEntryBytes(JarInputStream jis) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = jis.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static byte[] readEntryBytes(JarFile jarFile, JarEntry entry) throws Exception {
        try (InputStream is = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        }
    }

    /**
     * Loads a NightConfig {@link Config} directly from the given in‑memory descriptor bytes.
     * This avoids the costly round‑trip to the file‑system that the old implementation required.
     */
    private static Config loadConfigFromBytes(String descriptorPath, byte[] bytes) {
        try {
            if (descriptorPath.endsWith(".toml")) {
                try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                    return new TomlParser().parse(reader);
                }
            } else if (descriptorPath.endsWith(".json")) {
                try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                    return new JsonParser().parse(reader);
                }
            } else if (descriptorPath.endsWith(".info")) {
                String content = new String(bytes, StandardCharsets.UTF_8);

                String modId = null;
                String version = null;
                JsonElement root = new com.google.gson.JsonParser().parse(content);

                JsonObject obj = null;
                if (root.isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray();
                    if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                        obj = arr.get(0).getAsJsonObject();
                    }
                } else if (root.isJsonObject()) {
                    JsonObject rootObject = root.getAsJsonObject();
                    if (rootObject.has("modList")){
                        JsonArray arr = rootObject.getAsJsonArray("modList");
                        if (arr.size() > 0 && arr.get(0).isJsonObject()) {
                            obj = arr.get(0).getAsJsonObject();
                        }
                    }

                }

                if (obj != null) {
                    if (obj.has("modid") && !obj.get("modid").isJsonNull()) {
                        modId = obj.get("modid").getAsString();
                    }
                    if (obj.has("version") && !obj.get("version").isJsonNull()) {
                        version = obj.get("version").getAsString();
                    }
                }


                List<String> needed = new ArrayList<>();
                if (modId != null) {
                    needed.add("\"modid\": \"" + modId + "\"");
                }
                if (version != null) {
                    needed.add("\"version\": \"" + version + "\"");
                }
                content = "{\"mods\":[{" + String.join(",", needed) + "}]}";
                try (Reader reader = new StringReader(content)) {
                    return new JsonParser().parse(reader);
                }
            } else {
                throw new IllegalArgumentException("Unsupported descriptor file extension: " + descriptorPath);
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed to parse descriptor " + descriptorPath + " in‑memory", e);
            return null;
        }
    }

    private static ManifestParsingResult parseManifest(Manifest manifest) {
        if (manifest == null) return null;

        Attributes a = manifest.getMainAttributes();
        String implVer = a.getValue(Attributes.Name.IMPLEMENTATION_VERSION);

        List<String> mixin = new ArrayList<>();
        String mixinCfgs = a.getValue("MixinConfigs");
        if (mixinCfgs != null) {
            for (String s : mixinCfgs.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) mixin.add(t);
            }
        }
        return new ManifestParsingResult(implVer, mixin);
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
