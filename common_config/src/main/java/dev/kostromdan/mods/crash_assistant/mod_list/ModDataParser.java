package dev.kostromdan.mods.crash_assistant.mod_list;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ModDataParser {
    public static List<String> tomlPaths = new ArrayList<>() {{
        add("META-INF/mods.toml");
        add("META-INF/neoforge.mods.toml");
    }};

    public static Mod parseModData(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            for (String resourcePath : tomlPaths) {
                JarEntry entry = jarFile.getJarEntry(resourcePath);
                if (entry == null) continue;
                try {
                    Path temp = Files.createTempFile("moddata", ".toml");
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try (FileConfig config = FileConfig.builder(temp).build()) {
                        config.load();
                        ArrayList<Object> modsList = config.get("mods");
                        if (modsList == null || modsList.isEmpty()) continue;
                        Config mods = (Config) modsList.getFirst();
                        String modId = mods.get("modId");
                        String version = mods.get("version");
                        if (Objects.equals(version, "${file.jarVersion}")) {
                            version = parseVersionFromManifest(jarFile);
                        }
                        return new Mod(jarPath.getFileName().toString(), modId, version);
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                } catch (Exception e) {
                    JarInJarHelper.LOGGER.error("Error while trying to parse " + resourcePath + " of " + jarPath.getFileName().toString(), e);
                }
            }
            try {
                JarEntry jsonEntry = jarFile.getJarEntry("fabric.mod.json");
                if (jsonEntry != null) {
                    Path temp = Files.createTempFile("moddata", ".json");
                    try (InputStream is = jarFile.getInputStream(jsonEntry)) {
                        Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try (FileConfig config = FileConfig.builder(temp).build()) {
                        config.load();
                        String modId = config.get("id");
                        String version = config.get("version");
                        return new Mod(jarPath.getFileName().toString(), modId, version);
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
            } catch (Exception e) {
                JarInJarHelper.LOGGER.error("Error while trying to parse fabric.mod.json of " + jarPath.getFileName().toString(), e);
            }
        } catch (Exception ignored) {
        }
        return new Mod(jarPath.getFileName().toString(), null, null);
    }

    private static String parseVersionFromManifest(JarFile jarFile) {
        JarEntry manifestEntry = jarFile.getJarEntry("META-INF/MANIFEST.MF");
        if (manifestEntry == null) return null;
        try (InputStream is = jarFile.getInputStream(manifestEntry)) {
            Manifest manifest = new Manifest(is);
            return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        } catch (Exception ignored) {
        }
        return null;
    }
}
