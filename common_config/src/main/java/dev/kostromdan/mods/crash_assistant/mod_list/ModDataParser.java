package dev.kostromdan.mods.crash_assistant.mod_list;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;

import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ModDataParser {
    public static List<String> tomlPaths = new ArrayList<>() {{
        add("META-INF/mods.toml");
        add("META-INF/neoforge.mods.toml");
    }};

    public static Mod parseModData(Path jarPath) {
        try (FileSystem zipFs = FileSystems.newFileSystem(jarPath)) {
            for (String path : tomlPaths) {
                try {
                    Path modsTomlPath = zipFs.getPath(path);
                    if (!Files.exists(modsTomlPath)) {
                        continue;
                    }
                    try (FileConfig config = FileConfig.builder(modsTomlPath).build()) {
                        config.load();
                        Config mods = (Config) ((ArrayList) config.get("mods")).get(0);
                        String modId = mods.get("modId");
                        String version = mods.get("version");
                        if (Objects.equals(version, "${file.jarVersion}")) {
                            version = parseVersionFromManifest(zipFs);
                        }
                        return new Mod(jarPath.getFileName().toString(), modId, version);
                    }

                } catch (Exception e) {
                    JarInJarHelper.LOGGER.error(e);
                }
            }
            fabric:
            try {
                Path jsonPath = zipFs.getPath("fabric.mod.json");
                if (!Files.exists(jsonPath)) {
                    break fabric;
                }
                try (FileConfig config = FileConfig.builder(jsonPath).build()) {
                    config.load();
                    String modId = config.get("id");
                    String version = config.get("version");
                    return new Mod(jarPath.getFileName().toString(), modId, version);
                }
            } catch (Exception e) {
                JarInJarHelper.LOGGER.error(e);
            }

        } catch (Exception ignored) {
        }
        return new Mod(jarPath.getFileName().toString(), null, null);
    }

    private static String parseVersionFromManifest(FileSystem fs) {
        Path manifestPath = fs.getPath("META-INF/MANIFEST.MF");

        if (!Files.exists(manifestPath)) return null;
        try (InputStream is = Files.newInputStream(manifestPath)) {
            Manifest manifest = new Manifest(is);
            return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        } catch (Exception ignored) {
        }
        return null;
    }
}
