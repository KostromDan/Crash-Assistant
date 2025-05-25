package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.ProblematicModsConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.IncompatibleMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModDataParser;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Core;
import oshi.SystemInfo;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;

public class JarInJarHelper {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantJarInJarHelper");
    public static boolean isClient = false;

    public static void launchCrashAssistantApp(String launchTarget) {
        if (!launchTarget.toLowerCase().contains("client")) {
            LOGGER.warn("launchTarget: " + launchTarget + ". Crash Assistant is client only mod. Mod will do nothing!");
            return;
        }
        isClient = true;
        try {
            Path crashAssistantModJarPath = Paths.get(LibrariesJarLocator.getLibraryJarPath(JarInJarHelper.class)).toAbsolutePath();
            LOGGER.info("Launching CrashAssistantApp ({})", crashAssistantModJarPath.getFileName().toString());

            ProcessHandle currentProcess = ProcessHandle.current();
            String currentProcessData = Objects.toString(currentProcess.pid()) + "_"
                    + Objects.toString(currentProcess.info().startInstant().get().getEpochSecond());
            Path extractedJarPath = extractJarInJar("app.jar", currentProcessData + "_app.jar");

            String childProcess = String.join("\n", ProcessHandle.current().children().map(child -> child.pid() + ":" + child.info().startInstant().get().toEpochMilli()).toList());
            if (!childProcess.isEmpty()) {
                PlatformHelp.childProcessesPIDs = childProcess;
            }

            ProcessBuilder crashAssistantAppProcessBuilder = new ProcessBuilder(
                    JavaBinaryLocator.getJavaBinary(),
                    "-XX:+UseSerialGC",
                    "-XX:MaxHeapFreeRatio=30",
                    "-XX:MinHeapFreeRatio=10",
                    "-XX:MaxGCPauseMillis=10000",
                    "-Xms8m",
                    "-Xmx512m",
                    "-jar", extractedJarPath.toAbsolutePath().toString(),
                    "-jarPath", extractedJarPath.toAbsolutePath().toString(),
                    "-parentPID", Objects.toString(ProcessHandle.current().pid()),
                    "-parentStarted", Objects.toString(ProcessHelper.getStartTime(ProcessHandle.current().pid())),
                    "-parentXms", getJvmArgValue("Xms", "unknown"),
                    "-parentXmx", getJvmArgValue("Xmx", "unknown"),
                    "-systemRAM", formatMemorySize(new SystemInfo().getHardware().getMemory().getTotal()),
                    "-platform", PlatformHelp.platform.toString(),
                    "-loaderJarName", PlatformHelp.loaderJarName,
                    "-minecraftVersion", PlatformHelp.minecraftVersion,
                    "-childProcessesPIDs", Base64.getEncoder().encodeToString(PlatformHelp.childProcessesPIDs.getBytes(StandardCharsets.UTF_8)),
                    "-crashAssistantModJarPath", crashAssistantModJarPath.toString(),
                    "-log4jApi", LibrariesJarLocator.getLibraryJarPath(LogManager.class),
                    "-log4jCore", LibrariesJarLocator.getLibraryJarPath(Core.class),
                    "-googleGson", LibrariesJarLocator.getLibraryJarPath(Gson.class),
                    "-commonIo", LibrariesJarLocator.getLibraryJarPath(ReversedLinesFileReader.class),
                    "-processor", new SystemInfo().getHardware().getProcessor().getProcessorIdentifier().getName()
            );
            crashAssistantAppProcessBuilder.start();
            ProblematicModsConfig.crashIfProblematicMod();
        } catch (Exception e) {
            LOGGER.error("Error while launching GUI: ", e);
        }
    }

    public static List<Path> getModJarPathsContainingPart(String part) {
        try {
            return Files.list(Paths.get("mods"))
                    .filter(path -> Files.isRegularFile(path) &&
                            path.getFileName().toString().toLowerCase().contains(part.toLowerCase()) &&
                            path.getFileName().toString().endsWith(".jar"))
                    .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Mod> mapPathsToMods(List<Path> paths) {
        return paths.stream()
                .map(ModDataParser::parseModData)
                .toList();
    }

    public static List<Mod> getModsContainingPart(String... parts) {
        Set<Mod> resultSet = new HashSet<>();
        for (String part : parts) {
            resultSet.addAll(mapPathsToMods(getModJarPathsContainingPart(part)));
        }
        return new ArrayList<>(resultSet);
    }


    public static List<Mod> checkDuplicatedCrashAssistantMod(boolean crashIfDuplicated) {
        try {
            List<Mod> mods = getModsContainingPart("crash_assistant-", "CrashAssistant-");
            if (mods.size() < 2) return List.of();
            List<Mod> modsWithSameModId = mods.stream().filter(mod -> Objects.equals(mod.getModId(), "crash_assistant")).toList();
            String duplicatedMods = String.join("\n", mods.stream().map(Mod::getJarName).toList());
            if (modsWithSameModId.size() > 1) {
                LOGGER.error("Found more than one mod with modid \"crash_assistant\". Crash Assistant is duplicated." + (crashIfDuplicated ? " Crashing!" : "") +
                        "\nDuplicated mods:\n" + duplicatedMods);
                if (crashIfDuplicated) System.exit(-1);
            } else {
                LOGGER.error("Found more than one mod starting with \"crash_assistant-\":\n" +
                        duplicatedMods + "\n" +
                        "Assuming Crash Assistant is duplicated. Duplicated coremods can produce wired issues.");
            }
            return mods;
        } catch (Exception e) {
            LOGGER.error("Error while checking duplicated mods", e);
            return List.of();
        }
    }

    public static Optional<IncompatibleMod> checkForIncompatibleMods(boolean crashIfIncompatibleModDetected) {
        if (!isClient && crashIfIncompatibleModDetected) {
            return Optional.empty();
        }
        for (IncompatibleMod incompatibleMod : IncompatibleMod.incompatibleMods) {
            List<Path> modPaths = getModJarPathsContainingPart(incompatibleMod.getJarNamePart());
            if (modPaths.isEmpty()) continue;

            List<Mod> mods = mapPathsToMods(modPaths).stream().filter(mod -> Objects.equals(mod.getModId(), incompatibleMod.getModId())).toList();
            if (mods.isEmpty()) continue;
            incompatibleMod.addDetectedMods(mods);

            if (crashIfIncompatibleModDetected) {
                String incompatibleModsString = String.join(", ", mods.stream().map(Mod::getJarName).toList());
                String crashAssistantString = "Crash Assistant";
                try {
                    crashAssistantString = Paths.get(LibrariesJarLocator.getLibraryJarPath(JarInJarHelper.class)).getFileName().toString();
                } catch (Exception ignored) {
                }
                String incompatibleMessage = crashAssistantString + " and " + incompatibleModsString + "are incompatible.";
                if (CrashAssistantConfig.getBoolean("compatibility.enabled")) {
                    JarInJarHelper.LOGGER.error("Crash Assistant detected incompatible mod(s), crashing to prevent potential issues:\n{}",
                            incompatibleMessage + " Remove one of them.");
                    System.exit(-1);
                } else {
                    JarInJarHelper.LOGGER.warn("Crash Assistant detected incompatible mod(s). Compatibility check is disabled! Issues may arise!\n{}",
                            incompatibleMessage + " Continue at your own risk!");
                }

            }
            return Optional.of(incompatibleMod);
        }
        return Optional.empty();
    }

    public static Path extractJarInJar(String embeddedName, String outputName) throws IOException {
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

    public static void unzipFromJar(String embeddedPath, Path extractedPath) {
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

    public static HashMap<String, String> readJsonFromJar(String embeddedPath) {
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


    public static HashMap<String, String> readJsonFromFile(Path path) {
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

    public static void writeJsonToFile(Map<String, String> json, Path path) {
        try {
            try (FileWriter writer = new FileWriter(path.toFile())) {
                Gson GSON = new GsonBuilder().setPrettyPrinting().create();
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Error while saving json " + path, e);
        }
    }

    public static HashMap<String, String> convertJsonToMap(JsonObject json) {
        HashMap<String, String> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return values;
    }

    public static Path getJarInJar(String name) throws IOException, URISyntaxException {
        //Idea taken from org.sinytra.connector.locator.EmbeddedDependencies#getJarInJar
        Path pathInModFile = Path.of(JarInJarHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("META-INF/jarjar/" + name);
        URI filePathUri = new URI("jij:" + pathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
        Map<String, ?> outerFsArgs = Map.of("packagePath", pathInModFile);
        FileSystem zipFS = FileSystems.newFileSystem(filePathUri, outerFsArgs);
        return zipFS.getPath("/");
    }

    /**
     * Retrieves the value of a JVM argument from the current runtime.
     *
     * @param argName  The name of the JVM argument to retrieve (without the leading dash), e.g., "Xmx"
     * @param fallback The fallback value to return if the argument is not found
     * @return The value of the JVM argument if found, otherwise the fallback value
     */
    public static String getJvmArgValue(String argName, String fallback) {
        try {
            List<String> inputArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : inputArgs) {
                if (arg.startsWith("-" + argName)) {
                    // If the argument is in the form -Xmx512m, extract just the 512m part
                    if (arg.length() > argName.length() + 1) {
                        return arg.substring(argName.length() + 1);
                    }
                    return arg.substring(1); // Remove the leading dash if no value part
                }
            }

            // For Xmx, use current allocated memory as fallback if requested
            if (argName.equals("Xmx") && fallback.equals("unknown")) {
                return formatMemorySize(Runtime.getRuntime().maxMemory());
            }

            return fallback;
        } catch (Exception e) {
            LOGGER.error("Error retrieving JVM argument {}: {}", argName, e.getMessage());
            return fallback;
        }
    }

    /**
     * Formats memory size in bytes to a human-readable format suitable for Xmx/Xms arguments.
     *
     * @param bytes Memory size in bytes
     * @return Formatted memory size (e.g., "512m", "2.5g")
     */
    private static String formatMemorySize(long bytes) {
        if (bytes >= 1073741824) { // 1 GB
            double gb = bytes / 1073741824.0;
            // Format with one decimal place and remove trailing zero if it's a whole number
            String formatted = String.format(Locale.US, "%.1f", gb).replace(".0", "");
            return formatted + "g";
        } else {
            double mb = bytes / 1048576.0;
            String formatted = String.format(Locale.US, "%.1f", mb).replace(".0", "");
            return formatted + "m"; // Convert to MB
        }
    }
}