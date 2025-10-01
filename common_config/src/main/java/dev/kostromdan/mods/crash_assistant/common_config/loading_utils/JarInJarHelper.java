package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.sun.management.OperatingSystemMXBean;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.ProblematicModsConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.IncompatibleMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModDataParser;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.stream.Collectors;

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
            Path originalModJarPath = Paths.get(LibrariesJarLocator.getOurModJarPath()).toAbsolutePath();
            LOGGER.info("Launching CrashAssistantApp ({})", originalModJarPath.getFileName().toString());

            long currentProcessId = ProcessHelper.getCurrentProcessId();
            String currentProcessData = Objects.toString(currentProcessId) + "_"
                    + Objects.toString(ProcessHelper.getCurrentProcessStartTime());
            killAndDeleteOldApps();

            Path tempDir = Paths.get("local", "crash_assistant");
            Path tempAppJarPath = extractJarInJar("app.jar", currentProcessData + "_app.jar");
            Path tempModJarPath = tempDir.resolve(currentProcessData + "_mod.jar");
            Files.copy(originalModJarPath, tempModJarPath, StandardCopyOption.REPLACE_EXISTING);

            String childProcess = ProcessHelper.getChildProcessesInfo();
            if (!childProcess.isEmpty()) {
                PlatformHelp.childProcessesPIDs = childProcess;
            }

            List<String> classPathEntries = new ArrayList<>();
            classPathEntries.add(tempAppJarPath.toString());
            classPathEntries.add(tempModJarPath.toString());
            for (Class<?> clazz : ProcessHelper.getNeededForAppClasses()) {
                classPathEntries.add(LibrariesJarLocator.getLibraryJarPath(clazz));
            }
            String fullClassPath = String.join(System.getProperty("path.separator"), classPathEntries);

            List<String> argsList = new ArrayList<>();
            argsList.add("-jarPath");
            argsList.add(tempAppJarPath.toString());
            argsList.add("-parentPID");
            argsList.add(Objects.toString(ProcessHelper.getCurrentProcessId()));
            argsList.add("-parentStarted");
            argsList.add(Objects.toString(ProcessHelper.getCurrentProcessStartTime()));
            argsList.add("-platform");
            argsList.add(PlatformHelp.platform.toString());
            argsList.add("-loaderJarName");
            argsList.add(PlatformHelp.loaderJarName);
            argsList.add("-minecraftVersion");
            argsList.add(PlatformHelp.minecraftVersion);
            argsList.add("-childProcessesPIDs");
            argsList.add(Base64.getEncoder().encodeToString(PlatformHelp.childProcessesPIDs.getBytes(StandardCharsets.UTF_8)));
            argsList.add("-crashAssistantModJarName");
            argsList.add(originalModJarPath.getFileName().toString());
            argsList.add("-classPath");
            argsList.add(fullClassPath);
            argsList.add("-parentXms");
            argsList.add(getJvmArgValue("Xms", "unknown"));
            argsList.add("-parentXmx");
            argsList.add(getJvmArgValue("Xmx", "unknown"));
            argsList.add("-systemRAM");
            argsList.add(formatMemorySize(getTotalPhysicalMemory()));
            argsList.add("-processor");
            argsList.add(Base64.getEncoder().encodeToString(ProcessHelper.getProcessorName().getBytes(StandardCharsets.UTF_8)));
            if (PlatformHelp.modLoadedWithConnector) {
                argsList.add("-modLoadedWithConnector");
            }

            Path argsFile = Paths.get("local", "crash_assistant", currentProcessData + "_args.info");
            Files.write(argsFile, argsList, StandardCharsets.UTF_8);

            ProcessBuilder crashAssistantAppProcessBuilder = new ProcessBuilder(
                    JavaBinaryLocator.getJavaBinary(),
                    "-XX:+UseSerialGC",
                    "-XX:MaxHeapFreeRatio=30",
                    "-XX:MinHeapFreeRatio=10",
                    "-XX:MaxGCPauseMillis=10000",
                    "-Xms8m",
                    "-Xmx512m",
                    "-cp",
                    fullClassPath,
                    "dev.kostromdan.mods.crash_assistant.app.class_loading.Boot",
                    "--args-file",
                    argsFile.toString()
            );

            // Added by Embeddedt request. Since he is crashing very often for debugging and don't need window opening after crash.
            if ("true".equals(System.getenv("DisableEntirelyCrashAssistantModOnSystem"))) {
                LOGGER.error("Detected \"DisableEntirelyCrashAssistantModOnSystem\" env flag. Prevented start of Crash Assistant.");
                return;
            }

            Process crashAssistantAppProcess = crashAssistantAppProcessBuilder.start();
            ChildProcessLogger.captureOutput(crashAssistantAppProcess);
            ProblematicModsConfig.crashIfProblematicMod();
            JarInJarHelper.checkForIncompatibleMods(true);
        } catch (Throwable e) {
            LOGGER.error("Error while launching GUI: ", e);
        }
    }

    /**
     * Returns the total physical memory (RAM) in bytes, or -1 if the value
     * cannot be determined on the current JVM/OS.
     */
    public static long getTotalPhysicalMemory() {
        try {
            OperatingSystemMXBean osBean =
                    (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getTotalPhysicalMemorySize();  // value in bytes
        } catch (Throwable t) {
            // Either the cast failed (non-HotSpot VM) or the method is unavailable
            return -1L;
        }
    }

    public static List<Path> getModJarPathsContainingPart(String part) {
        try {
            return Files.list(Paths.get("mods"))
                    .filter(path -> Files.isRegularFile(path) &&
                            path.getFileName().toString().toLowerCase().contains(part.toLowerCase()) &&
                            path.getFileName().toString().endsWith(".jar"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Mod> mapPathsToMods(List<Path> paths) {
        return paths.stream()
                .map(ModDataParser::parseModData)
                .collect(Collectors.toList());
    }

    public static List<Mod> getModsContainingPart(String... parts) {
        Set<Mod> resultSet = new HashSet<>();
        for (String part : parts) {
            resultSet.addAll(mapPathsToMods(getModJarPathsContainingPart(part)));
        }
        return new ArrayList<>(resultSet);
    }

    public static boolean isCleanroomRelauncher() {
        HashSet<String> relauncherModIds = new HashSet<>(Arrays.asList("cleanroom-relauncher", "improved-relauncher", "relauncher"));
        List<Mod> mods = getModsContainingPart("cleanroom", "relauncher");
        mods = mods.stream().filter(mod -> relauncherModIds.contains(mod.getModId())).collect(Collectors.toList());
        if (mods.isEmpty()) return false;
        if (!ClassExistenceChecker.classExists("com.cleanroommc.boot.Main")) {
            LOGGER.warn("Detected cleanroom-relauncher env. Crash Assistant will start after relaunching with cleanroom.");
            return true;
        }
        return false;
    }

    public static Optional<Long> getCleanroomRelauncherParentPid() {
        String pid = System.getProperty("cleanroom.relauncher.parent");
        if (pid == null) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(pid));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static List<Mod> checkDuplicatedCrashAssistantMod(boolean crashIfDuplicated) {
        try {
            List<Mod> mods = getModsContainingPart("crash_assistant-", "CrashAssistant-");
            if (mods.size() < 2) return new ArrayList<>();
            List<Mod> modsWithSameModId = mods.stream().filter(mod -> Objects.equals(mod.getModId(), "crash_assistant")).collect(Collectors.toList());
            String duplicatedMods = String.join("\n", mods.stream().map(Mod::getJarName).collect(Collectors.toList()));
            if (modsWithSameModId.size() > 1) {
                LOGGER.error("Found more than one mod with modid \"crash_assistant\". Crash Assistant is duplicated." + (crashIfDuplicated ? " Crashing!" : "") +
                        "\nDuplicated mods:\n" + duplicatedMods);
                if (crashIfDuplicated) ProcessHelper.exitProcess(-1);
            } else {
                LOGGER.error("Found more than one mod starting with \"crash_assistant-\":\n" +
                        duplicatedMods + "\n" +
                        "Assuming Crash Assistant is duplicated. Duplicated coremods can produce wired issues.");
            }
            return mods;
        } catch (Exception e) {
            LOGGER.error("Error while checking duplicated mods", e);
            return new ArrayList<>();
        }
    }

    public static Optional<IncompatibleMod> checkForIncompatibleMods(boolean crashIfIncompatibleModDetected) {
        if (!isClient && crashIfIncompatibleModDetected) {
            return Optional.empty();
        }
        for (IncompatibleMod incompatibleMod : IncompatibleMod.incompatibleMods) {
            List<Path> modPaths = getModJarPathsContainingPart(incompatibleMod.getJarNamePart());
            if (modPaths.isEmpty()) continue;

            List<Mod> mods = mapPathsToMods(modPaths).stream().filter(mod -> Objects.equals(mod.getModId(), incompatibleMod.getModId())).collect(Collectors.toList());
            if (mods.isEmpty()) continue;
            incompatibleMod.addDetectedMods(mods);
            if (crashIfIncompatibleModDetected) {
                String incompatibleModsString = String.join(", ", mods.stream().map(Mod::getJarName).collect(Collectors.toList()));
                String crashAssistantString = "Crash Assistant";
                try {
                    crashAssistantString = Paths.get(LibrariesJarLocator.getOurModJarPath()).getFileName().toString();
                } catch (Exception ignored) {
                }
                String incompatibleMessage = crashAssistantString + " and " + incompatibleModsString + "are incompatible.";
                if (CrashAssistantConfig.getBoolean("compatibility.enabled")) {
                    JarInJarHelper.LOGGER.error("Crash Assistant detected incompatible mod(s), crashing to prevent potential issues:\n{}",
                            incompatibleMessage + " Remove one of them.");
                    ProcessHelper.exitProcess(-1);
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

        unzipFromJar("/META-INF/jarjar/" + embeddedName, extractedJarPath);

        return extractedJarPath;
    }

    public static void killAndDeleteOldApps() throws IOException {
        Path outputDirectory = Paths.get("local", "crash_assistant");
        if (!Files.exists(outputDirectory)) return;
        Files.list(outputDirectory).forEach(path -> {
            String fileName = path.getFileName().toString();
            if (Files.isRegularFile(path) && fileName.endsWith("app.jar")) {
                String processInfo = fileName.split("_app.jar")[0];
                Path tmpModLibJarPath = outputDirectory.resolve(processInfo + "_mod.jar");
                Path processInfoPath = outputDirectory.resolve(processInfo + ".info");
                Path argsInfoPath = outputDirectory.resolve(processInfo + "_args.info");
                Path oldCSPath = outputDirectory.resolve(processInfo + "_gpu_detect.cs");
                Path oldDllPath = outputDirectory.resolve(processInfo + "_gpu-detect-jni.dll");
                try {
                    if (Files.exists(processInfoPath)) {
                        if (CrashAssistantConfig.getBoolean("general.kill_old_app")) {
                            Long minecraft_pid = Long.parseLong(processInfo.split("_")[0]);
                            Long start_time = Long.parseLong(processInfo.split("_")[1]);
                            Long app_pid;
                            Long app_start_time = null;
                            try {
                                byte[] bytes = Files.readAllBytes(processInfoPath);
                                String content = new String(bytes, StandardCharsets.UTF_8);
                                if (content.contains(" : ")) {
                                    app_pid = Long.parseLong(content.split(" : ")[0].trim());
                                    app_start_time = Long.parseLong(content.split(" : ")[1].trim());
                                } else {
                                    app_pid = Long.parseLong(content);
                                }
                            } catch (IOException ex) {
                                LOGGER.error("Error while reading " + processInfoPath + ". This should never happen:", ex);
                                throw new RuntimeException(ex);
                            }
                            boolean isAppProcessAlive = ProcessHelper.isProcessAlive(app_pid);
                            long minecraftStartTime = ProcessHelper.getProcessStartTime(minecraft_pid);
                            if (isAppProcessAlive && (app_start_time == null || app_start_time == ProcessHelper.getProcessStartTime(app_pid))) {
                                boolean oldMinecraftIsAlive = ProcessHelper.isProcessAlive(minecraft_pid) && minecraftStartTime == start_time;
                                boolean isOldPidIsRelauncher = false;
                                if (Objects.equals(PlatformHelp.minecraftVersion, "1.12.2")) {
                                    Optional<Long> cleanroomRelauncherParentPid = getCleanroomRelauncherParentPid();
                                    if (cleanroomRelauncherParentPid.isPresent()) {
                                        isOldPidIsRelauncher = minecraft_pid.equals(cleanroomRelauncherParentPid.get());
                                    }
                                }


                                if (oldMinecraftIsAlive && isOldPidIsRelauncher) {
                                    LOGGER.warn("Closed old CrashAssistantApp process since relaunch with Cleanroom happened.");
                                } else if (!oldMinecraftIsAlive) {
                                    LOGGER.warn("Closed old CrashAssistantApp process to prevent confusing the player with window containing information from old crash.");
                                }
                                if (!oldMinecraftIsAlive || isOldPidIsRelauncher) {
                                    ProcessHelper.destroyProcess(app_pid);
                                    new java.util.Timer().schedule(
                                            new java.util.TimerTask() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        Files.deleteIfExists(path);
                                                        Files.deleteIfExists(tmpModLibJarPath);
                                                        Files.deleteIfExists(processInfoPath);
                                                        Files.deleteIfExists(argsInfoPath);
                                                        Files.deleteIfExists(oldCSPath);
                                                        Files.deleteIfExists(oldDllPath);
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
                } catch (Exception e) {
                    LOGGER.warn("Error while removing '{}' or reading '{}'. This should never happen. Maybe files got corrupted?", fileName, processInfoPath.getFileName(), e);
                }
                try {
                    Files.deleteIfExists(path);
                    Files.deleteIfExists(tmpModLibJarPath);
                    Files.deleteIfExists(processInfoPath);
                    Files.deleteIfExists(argsInfoPath);
                    Files.deleteIfExists(oldCSPath);
                    Files.deleteIfExists(oldDllPath);
                } catch (IOException ignored) {
                }
            } else if (Files.isRegularFile(path) && (fileName.endsWith(".info") || fileName.endsWith("_mod.jar") || fileName.endsWith("_gpu_detect.cs") || fileName.endsWith("_gpu-detect-jni.dll")) && fileName.contains("_")) {
                String processInfo;
                if (fileName.endsWith("_args.info")) {
                    processInfo = fileName.split("_args\\.info")[0];
                } else if (fileName.endsWith(".info")) {
                    processInfo = fileName.split("\\.info")[0];
                } else if (fileName.endsWith(".cs")) {
                    processInfo = fileName.split("_gpu_detect\\.cs")[0];
                } else if (fileName.endsWith(".dll")) {
                    processInfo = fileName.split("_gpu-detect-jni\\.dll")[0];
                } else {
                    processInfo = fileName.split("_mod\\.jar")[0];
                }

                if (!Files.exists(outputDirectory.resolve(processInfo + "_app.jar"))) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                }
            }
        });
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
                JsonElement jsonElement = new JsonParser().parse(reader);
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
                return convertJsonToMap(new JsonParser().parse(reader).getAsJsonObject());
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
        Path pathInModFile = Paths.get(JarInJarHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("META-INF/jarjar/" + name);
        URI filePathUri = new URI("jij:" + pathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
        Map<String, Path> outerFsArgs = Collections.singletonMap("packagePath", pathInModFile);
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
