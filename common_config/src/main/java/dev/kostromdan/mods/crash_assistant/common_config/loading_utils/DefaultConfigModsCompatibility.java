package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class DefaultConfigModsCompatibility {
    private static final Path TARGET_DIR = Paths.get("config", "crash_assistant");
    private static final Path[] SOURCE_DIRS = {
            Paths.get("config", "yosbr", "config", "crash_assistant"),
            Paths.get("config", "modpack_defaults", "config", "crash_assistant")
    };

    public static void copyDefaultConfigs() {
        Path sourceDirToUse = null;

        for (Path sourceDir : SOURCE_DIRS) {
            if (Files.exists(sourceDir) && Files.isDirectory(sourceDir)) {
                sourceDirToUse = sourceDir;
                break;
            }
        }

        if (sourceDirToUse == null) {
            return;
        }

        try {
            if (!Files.exists(TARGET_DIR)) {
                Files.createDirectories(TARGET_DIR);
            }

            Path finalSourceDir = sourceDirToUse;

            try (Stream<Path> stream = Files.walk(finalSourceDir)) {
                stream.forEach(sourcePath -> {
                    try {
                        Path relativePath = finalSourceDir.relativize(sourcePath);
                        Path targetPath = TARGET_DIR.resolve(relativePath);

                        if (Files.isDirectory(sourcePath)) {
                            if (!Files.exists(targetPath)) {
                                Files.createDirectories(targetPath);
                            }
                        } else if (Files.isRegularFile(sourcePath)) {
                            if (!Files.exists(targetPath)) {
                                Files.copy(sourcePath, targetPath);
                                JarInJarHelper.LOGGER.info("Copied default config file from {} to {}", sourcePath, targetPath);
                            }
                        }
                    } catch (IOException e) {
                        JarInJarHelper.LOGGER.error("Failed to copy default config file {}", sourcePath, e);
                    }
                });
            }
        } catch (IOException e) {
            JarInJarHelper.LOGGER.error("Failed to process default configs from {}", sourceDirToUse, e);
        }
    }
}