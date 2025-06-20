package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.json.JsonParser;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
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
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
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

        try (InputStream fis = Files.newInputStream(jarPath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             JarInputStream jis = new JarInputStream(bis)) {

            Mod mod = parseJarFile(jis, jarPath.getFileName().toString(), null);
            saveModToCache(jarPath, mod);
            return mod;
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed to parse " + jarPath.getFileName() + ": ", e);
            return new Mod(jarPath.getFileName().toString(), null, null, null,
                    new ArrayList<>(), new ArrayList<>(), null);
        }
    }

    private static Mod parseJarFile(JarInputStream jis, String currentJarName, String jarJarPath) {
        Boolean isMCreator = null;
        boolean hasEssentialLoader = false;

        List<String> mixinConfigs = new ArrayList<>();
        List<Mod> jarInJarMods = new ArrayList<>();
        Map<String, byte[]> descriptorBytes = new HashMap<>();

        ManifestParsingResult manifestResult = parseManifest(jis.getManifest());

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

                /* ─ Nested jars ─ */
                if (name.endsWith(".jar")) {
                    String nestedJarName = name.substring(name.lastIndexOf('/') + 1);
                    String normalizedNestedJarName = nestedJarName.toLowerCase();
                    if (normalizedNestedJarName.contains("mixinextras") || normalizedNestedJarName.contains("mixinsquared")) {
                        continue;
                    }
                    String nestedJarPath = "/" + (name.contains("/") ? name.substring(0, name.lastIndexOf('/') + 1) : "");
                    byte[] nestedBytes = readEntryBytes(jis);
                    try (JarInputStream nestedJis = new JarInputStream(new ByteArrayInputStream(nestedBytes))) {
                        Mod nested = parseJarFile(nestedJis, nestedJarName, nestedJarPath);
                        nested = new Mod(nestedJarName, nested.getModId(), nested.getVersion(),
                                nested.IsMCreator(), nested.getMixinConfigs(),
                                nested.getJarJarMods(), nestedJarPath);
                        jarInJarMods.add(nested);
                    } catch (Exception e) {
                        JarInJarHelper.LOGGER.warn("Error processing nested jar " + name + ": " + e.getMessage());
                        jarInJarMods.add(new Mod(name.substring(name.lastIndexOf('/') + 1),
                                null, null, null,
                                new ArrayList<>(), new ArrayList<>(),
                                "/" + (name.contains("/") ? name.substring(0, name.lastIndexOf('/') + 1) : "")));
                    }
                    continue;
                }

                /* ─ Descriptor collection ─ */
                if (inJarPaths.contains(name)) {
                    descriptorBytes.put(name, readEntryBytes(jis));
                }
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Failed while streaming entries of " + currentJarName, e);
        }

        /* ─ Parse descriptors in priority order ─ */
        for (String descriptorPath : inJarPaths) {
            byte[] bytes = descriptorBytes.get(descriptorPath);
            if (bytes == null) continue;

            try {
                Config cfg = loadConfigFromBytes(currentJarName + "/" + descriptorPath, bytes);
                if (cfg == null) continue;

                Config mods;
                String modId;
                ManifestParsingResult mp = null;

                if (descriptorPath.endsWith(".toml")) {
                    List<Object> modsList = cfg.get("mods");
                    if (modsList == null || modsList.isEmpty()) continue;

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
                        mp = manifestResult;
                        if (mp != null) mixinConfigs.addAll(mp.getMixinConfigs());
                    }
                } else {
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
                }

                String version = mods.get("version");
                if (Objects.equals(version, "${file.jarVersion}")) {
                    if (mp == null) mp = manifestResult;
                    version = mp == null ? null : mp.getImplementationVersion();
                } else if (Objects.equals(version, "${modVersion}")) {
                    version = null;
                }

                if (version == null && modId == null) {
                    throw new Exception("Failed to parse mod data (version AND modId) from " +
                            descriptorPath + " of " + currentJarName);
                }
                if (version == null) JarInJarHelper.LOGGER.warn("Failed to parse version from " +
                        descriptorPath + " of " + currentJarName);
                if (modId == null) JarInJarHelper.LOGGER.warn("Failed to parse modId from " +
                        descriptorPath + " of " + currentJarName);

                return new Mod(currentJarName, modId, version,
                        isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
            } catch (Exception e) {
                JarInJarHelper.LOGGER.warn("Error parsing " + descriptorPath + " of " +
                        currentJarName + ": ", e);
            }
        }

        /* ─ Special-case Essential ─ */
        if (currentJarName.toLowerCase().contains("essential") && hasEssentialLoader) {
            return new Mod(currentJarName, "essential-container", null,
                    isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
        }

        /* ─ Nothing found ─ */
        return new Mod(currentJarName, null, null,
                isMCreator, mixinConfigs, jarInJarMods, jarJarPath);
    }

    /* ─────────────────────────────────────────────
     *  Helpers
     * ───────────────────────────────────────────── */
    private static byte[] readEntryBytes(JarInputStream jis) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = jis.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }


    private static Config loadConfigFromBytes(String descriptorPath, byte[] bytes) {
        if (!descriptorPath.endsWith(".toml")) {
            try (Reader reader = new InputStreamReader(
                    new ByteArrayInputStream(bytes),
                    StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader);
            } catch (Exception e) {
                JarInJarHelper.LOGGER.warn("Failed to parse JSON descriptor " + descriptorPath, e);
                return null;
            }
        }

        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8)) {
            return new TomlParser().parse(reader);
        } catch (ParsingException pe) {
            byte[] normalized = normalizeToml(bytes);
            try (Reader reader2 = new InputStreamReader(
                    new ByteArrayInputStream(normalized),
                    StandardCharsets.UTF_8)) {
                return new TomlParser().parse(reader2);
            } catch (Exception e2) {
                JarInJarHelper.LOGGER.warn("Failed to parse descriptor even after normalization: "
                        + descriptorPath, e2);
                return null;
            }
        } catch (Exception e) {
            JarInJarHelper.LOGGER.warn("Unexpected error parsing TOML descriptor " + descriptorPath, e);
            return null;
        }
    }

    /**
     * Restores invalid “newline in basic string” sequences that some very old mods
     * wrote out in 8-bit encodings.
     * <p>
     * Algorithm:
     * – stream over the raw bytes
     * – remember whether we are inside a single-line basic string ( " … " )
     * but **not** inside a multi-line one ( """ … """ ), and whether the
     * current byte is escaped with a back-slash
     * – whenever an unescaped LF (`0x0A`) is seen *while* we are inside a
     * single-line basic string, emit the two ASCII characters `\` `n` instead
     * of the raw LF
     */
    private static byte[] normalizeToml(byte[] raw) {
        StringBuilder out = new StringBuilder(raw.length + 16);
        boolean inBasic = false;        // inside " … "
        boolean inMultiline = false;    // inside """ … """
        boolean escaped = false;        // previous char was '\'
        int quoteRun = 0;               // how many '"' seen in a row

        for (byte b : raw) {
            char c = (char) (b & 0xFF);

            // track quoting state -------------------------------------------------
            if (c == '"' && !escaped) {
                quoteRun++;
            } else {
                quoteRun = 0;
            }
            if (quoteRun == 1 && !inMultiline) {           // start / end basic
                inBasic = !inBasic;
            } else if (quoteRun == 3) {                    // start / end """ …
                inMultiline = !inMultiline;
                inBasic = false;                           // triple quotes override
                quoteRun = 0;                              // reset for safety
            }

            // newline → "\n" only if it’s really illegal --------------------------
            if (c == '\n' && inBasic && !escaped) {
                out.append("\\n");
                escaped = false;
                continue;
            }

            // regular copy --------------------------------------------------------
            out.append(c);
            escaped = (!escaped && c == '\\'); // '\' toggles escape for *next* byte
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
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

    /* ─────────────────────────────────────────────
     *  Old ManifestParsingResult (unchanged)
     * ───────────────────────────────────────────── */
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
