package dev.kostromdan.mods.crash_assistant.loading_utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Core;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public interface JarInJarHelper {
    Logger LOGGER = LogManager.getLogger("CrashAssistantJarInJarHelper");

    static void launchCrashAssistantApp(String launchTarget) {
        if (!launchTarget.toLowerCase().contains("client")) {
            LOGGER.warn("launchTarget: " + launchTarget + ". Crash Assistant is client only mod. Mod will do nothing!");
            return;
        }
        LOGGER.info("Launching CrashAssistantApp");
        try {
            ProcessHandle currentProcess = ProcessHandle.current();
            String currentProcessData = Objects.toString(currentProcess.pid()) + "_"
                    + Objects.toString(currentProcess.info().startInstant().get().getEpochSecond());
            Path extractedJarPath = extractJarInJar("app.jar", currentProcessData + "_app.jar");

            Optional<String> javaBinary = currentProcess.info().command();
            if (javaBinary.isEmpty()) {
                throw new IllegalStateException("Unable to determine the java binary path of current JVM. Crash Assistant won't work.");
            }

            ProcessBuilder crashAssistantAppProcessBuilder = new ProcessBuilder(
                    javaBinary.get(),
                    "-XX:+UseSerialGC",
                    "-XX:MaxHeapFreeRatio=30",
                    "-XX:MinHeapFreeRatio=10",
                    "-XX:MaxGCPauseMillis=10000",
                    "-Xms8m",
                    "-Xmx512m",
                    "-jar", extractedJarPath.toAbsolutePath().toString(),
                    "-parentPID", Objects.toString(ProcessHandle.current().pid()),
                    "-log4jApi", LibrariesJarLocator.getLibraryJarPath(LogManager.class),
                    "-log4jCore", LibrariesJarLocator.getLibraryJarPath(Core.class),
                    "-googleGson", LibrariesJarLocator.getLibraryJarPath(Gson.class),
                    "-commonIo", LibrariesJarLocator.getLibraryJarPath(ReversedLinesFileReader.class)
            );
            Process crashAssistantAppProcess = crashAssistantAppProcessBuilder.start();
            ChildProcessLogger.captureOutput(crashAssistantAppProcess);
            Path currentProcessDataPath = Paths.get("local", "crash_assistant", currentProcessData + ".info");
            try {
                Files.write(currentProcessDataPath, Long.toString(crashAssistantAppProcess.pid()).getBytes());
            } catch (IOException ignored) {
            }


        } catch (Exception e) {
            LOGGER.error("Error while launching GUI: ", e);
        }
    }

    static void checkDuplicatedCrashAssistantMod() {
        try {
            List<String> mods = Files.list(Paths.get("mods"))
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith("crash_assistant-"))
                    .map(path -> path.getFileName().toString())
                    .toList();
            if (mods.size() > 1) {
                LOGGER.error("Found more than one mod starting with \"crash_assistant-\":\n" +
                        String.join("\n", mods) +
                        "\nAssuming Crash Assistant is duplicated. Duplicated coremods can produce wired issues.");
            }
        } catch (Exception e) {
            LOGGER.error("Error while checking duplicated mods", e);
        }
    }

    static Path extractJarInJar(String embeddedName, String outputName) throws IOException {
        Path outputDirectory = Paths.get("local", "crash_assistant");
        if (!Files.exists(outputDirectory)) {
            Files.createDirectories(outputDirectory);
        }
        Path extractedJarPath = outputDirectory.resolve(outputName);

        Files.list(outputDirectory).forEach(path -> {
            String fileName = path.getFileName().toString();
            if (Files.isRegularFile(path) && fileName.endsWith("app.jar")) {
                String processInfo = fileName.split("_app.jar")[0];
                Path processInfoPath = outputDirectory.resolve(processInfo + ".info");
                try {
                    Files.deleteIfExists(path);
                    Files.deleteIfExists(processInfoPath);
                } catch (IOException e) {
                    if (Files.exists(processInfoPath)) {
                        if (CrashAssistantConfig.getBoolean("general.kill_old_app")) {
                            Long minecraft_pid = Long.parseLong(processInfo.split("_")[0]);
                            Long start_time = Long.parseLong(processInfo.split("_")[1]);
                            Long app_pid;
                            try {
                                app_pid = Long.parseLong(Files.readString(processInfoPath));
                            } catch (IOException ex) {
                                LOGGER.error("Error while reading " + processInfoPath + ". This should never happen:", ex);
                                throw new RuntimeException(ex);
                            }
                            Optional<ProcessHandle> minecraftProcess = ProcessHandle.of(minecraft_pid);
                            Optional<ProcessHandle> appProcess = ProcessHandle.of(app_pid);
                            if (appProcess.isPresent()
                                    && !(minecraftProcess.isPresent() && minecraftProcess.get().info().startInstant().get().getEpochSecond() == start_time)) {
                                LOGGER.warn("Closed old CrashAssistantApp process to prevent confusing the player with window containing information from old crash.");
                                appProcess.get().destroy();
                                new java.util.Timer().schedule(
                                        new java.util.TimerTask() {
                                            @Override
                                            public void run() {
                                                try {
                                                    Files.deleteIfExists(path);
                                                    Files.deleteIfExists(processInfoPath);
                                                } catch (IOException ignored) {
                                                }
                                            }
                                        },
                                        5000
                                );
                            }
                        }
                    }
                }
            } else if (Files.isRegularFile(path) && fileName.endsWith(".info") && fileName.contains("_")) {
                String processInfo = fileName.split("\\.info")[0];
                if (!Files.exists(outputDirectory.resolve(processInfo + "_app.jar"))) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                }
            }
        });

        unzipFromJar("/META-INF/jarjar/" + embeddedName, extractedJarPath);

        return extractedJarPath;
    }

    static void unzipFromJar(String embeddedPath, Path extractedPath) {
        if (!embeddedPath.startsWith("/")) {
            embeddedPath = "/" + embeddedPath;
        }
        try {
            InputStream jarStream = JarInJarHelper.class.getResourceAsStream(embeddedPath);
            if (jarStream == null) {
                throw new FileNotFoundException("Could not find embedded JAR: " + embeddedPath);
            }

            try (OutputStream out = Files.newOutputStream(extractedPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = jarStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to unzip file from jar " + embeddedPath, e);
        }
    }

    static HashMap<String, String> readJsonFromJar(String embeddedPath) {
        if (!embeddedPath.startsWith("/")) {
            embeddedPath = "/" + embeddedPath;
        }

        try (InputStream jarStream = JarInJarHelper.class.getResourceAsStream(embeddedPath)) {
            if (jarStream == null) {
                throw new FileNotFoundException("Could not find embedded JAR: " + embeddedPath);
            }

            try (InputStreamReader reader = new InputStreamReader(jarStream, StandardCharsets.UTF_8)) {
                JsonElement jsonElement = JsonParser.parseReader(reader);
                if (jsonElement == null || !jsonElement.isJsonObject()) {
                    throw new IllegalStateException("JSON content is not a valid JSON object.");
                }

                JsonObject jsonObject = jsonElement.getAsJsonObject();
                Type mapType = new TypeToken<HashMap<String, String>>() {
                }.getType();
                return new Gson().fromJson(jsonObject, mapType);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read json from jar: {}", embeddedPath, e);
            return new HashMap<>();
        }
    }


    static HashMap<String, String> readJsonFromFile(Path path) {
        try {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return convertJsonToMap(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("Failed to read corrupted json from file '{}'. Renaming to .bak", path, e);
            String fileName = path.getFileName().toString();
            try {
                path.toFile().renameTo(Paths.get(path.getParent().toString(), fileName + ".bak").toFile());
            } catch (Exception e1) {
                LOGGER.error("Failed to rename '" + fileName + "' to '" + fileName + ".bak': ", e1);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to read json from file " + path.toString(), e);
        }
        return new HashMap<>();
    }

    static void writeJsonToFile(Map<String, String> json, Path path) {
        try {
            try (FileWriter writer = new FileWriter(path.toFile())) {
                Gson GSON = new GsonBuilder().setPrettyPrinting().create();
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Error while saving json " + path, e);
        }
    }

    static HashMap<String, String> convertJsonToMap(JsonObject json) {
        HashMap<String, String> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return values;
    }
}
