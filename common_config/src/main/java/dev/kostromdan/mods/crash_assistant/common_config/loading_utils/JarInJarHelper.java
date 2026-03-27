package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.IncompatibleMod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModDataParser;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.StartupScriptManager;
import dev.kostromdan.mods.crash_assistant.common_config.utils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.Startup;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.kostromdan.mods.crash_assistant.common_config.utils.MemoryUtils.*;

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
            DefaultConfigModsCompatibility.copyDefaultConfigs();
            if (CrashAssistantConfig.getBoolean("general.generate_own_launcher_log")) LauncherLogger.redirectToFile();

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

            setupScripts();
            StartupScriptManager.runStartupSequence(tempAppJarPath, tempModJarPath);

            String childProcess = ProcessHelper.getChildProcessesInfo();
            if (!childProcess.isEmpty()) {
                PlatformHelp.childProcessesPIDs = childProcess;
            }

            List<String> classPathEntriesForAppProcess = new ArrayList<>();
            classPathEntriesForAppProcess.add(tempAppJarPath.toString());
            classPathEntriesForAppProcess.add(tempModJarPath.toString());
            classPathEntriesForAppProcess.addAll(ProcessHelper.getPathsToNeededLibs(ProcessHelper.getAppPredicates()));
            String fullClassPath = String.join(File.pathSeparator, classPathEntriesForAppProcess);

            List<String> argsList = new ArrayList<>();
            argsList.add("-jarPath");
            argsList.add(tempAppJarPath.toString());
            argsList.add("-parentPID");
            argsList.add(Objects.toString(ProcessHelper.getCurrentProcessId()));
            argsList.add("-parentStarted");
            argsList.add(Objects.toString(ProcessHelper.getCurrentProcessStartTime()));
            argsList.add("-platform");
            argsList.add(PlatformHelp.platform.toString());
            argsList.add("-platformOwerridenData");
            argsList.add(PlatformHelp.platform.serializePlatformData());
            argsList.add("-loaderJarName");
            argsList.add(PlatformHelp.loaderJarName);
            argsList.add("-minecraftVersion");
            argsList.add(PlatformHelp.minecraftVersion);
            argsList.add("-childProcessesPIDs");
            argsList.add(Base64.getEncoder().encodeToString(PlatformHelp.childProcessesPIDs.getBytes(StandardCharsets.UTF_8)));
            argsList.add("-minecraftStartCommand");
            argsList.add(Base64.getEncoder().encodeToString(ArgUtils.getSafeLaunchArgs().getBytes(StandardCharsets.UTF_8)));
            argsList.add("-minecraftJvmArgs");
            argsList.add(Base64.getEncoder().encodeToString(ArgUtils.getSafeJvmArgs().getBytes(StandardCharsets.UTF_8)));
            argsList.add("-minecraftClassPath");
            argsList.add(Base64.getEncoder().encodeToString(ArgUtils.getSafeClassPath().getBytes(StandardCharsets.UTF_8)));
            argsList.add("-crashAssistantModJarName");
            argsList.add(originalModJarPath.getFileName().toString());
            argsList.add("-classPath");
            argsList.add(fullClassPath);
            argsList.add("-minecraftXms");
            argsList.add(formatMemorySize(getJvmInitialHeapBytes()));
            argsList.add("-minecraftXmx");
            argsList.add(formatMemorySize(getJvmMaxHeapBytes()));
            argsList.add("-systemRAM");
            argsList.add(formatMemorySize(getSystemTotalMemoryBytes()));
            argsList.add("-systemUsedRAMAtMinecraftLaunchMoment");
            argsList.add(formatMemorySize(getSystemUsedMemoryBytes() - getJvmAllocatedMemoryBytes()));
            argsList.add("-systemSwapSpace");
            argsList.add(formatMemorySize((getSystemTotalSwapBytes())));
            argsList.add("-systemUsedSwapSpaceAtMinecraftLaunchMoment");
            argsList.add(formatMemorySize((getSystemUsedSwapBytes())));
            argsList.add("-systemDiskFreeSpaceAtMinecraftLaunchMoment");
            argsList.add(formatMemorySize((getDiskFreeSpaceBytes())));
            argsList.add("-mainClass");
            argsList.add(MemoryUtils.getMainClassName());
            argsList.add("-processor");
            argsList.add(Base64.getEncoder().encodeToString(ProcessHelper.getProcessorName().getBytes(StandardCharsets.UTF_8)));
            if (PlatformHelp.modLoadedWithConnector) {
                argsList.add("-modLoadedWithConnector");
            }

            List<ScriptWarning> bootWarnings = Startup.getBootWarnings();
            if (!bootWarnings.isEmpty()) {
                argsList.add("-bootWarnings");
                argsList.add(Base64.getEncoder().encodeToString(new GsonBuilder().create().toJson(bootWarnings).getBytes(StandardCharsets.UTF_8)));
            }

            List<ScriptWarning> startupWarnings = Startup.getCrashWarnings();
            if (!startupWarnings.isEmpty()) {
                argsList.add("-startupWarnings");
                argsList.add(Base64.getEncoder().encodeToString(new GsonBuilder().create().toJson(startupWarnings).getBytes(StandardCharsets.UTF_8)));
            }
            if (tempDir.toAbsolutePath().toString().contains(Paths.get("lunarclient", "offline", "multiver").toString()) &&
                    ClassExistenceChecker.classExists("com.moonsworth.lunar.ichor.api.IchorAPI")) {
                Path latestLog = LatestLogLocator.findLatestLogPath();
                if (latestLog != null) {
                    argsList.add("-customLatestLogPath");
                    argsList.add(latestLog.toString());
                }
            }

            Path argsFile = Paths.get("local", "crash_assistant", currentProcessData + "_args.info");
            Files.write(argsFile, argsList, StandardCharsets.UTF_8);


            List<String> jvmArgs = new ArrayList<>();
            jvmArgs.add(JavaBinaryLocator.getJavaBinary());
            jvmArgs.add("-XX:+UseSerialGC");
            jvmArgs.add("-XX:MaxHeapFreeRatio=30");
            jvmArgs.add("-XX:MinHeapFreeRatio=10");
            jvmArgs.add("-XX:MaxGCPauseMillis=10000");
            jvmArgs.add("--enable-native-access=ALL-UNNAMED");
            jvmArgs.add("-Xms8m");
            jvmArgs.add("-Xmx512m");
            if (CrashAssistantConfig.getBoolean("general.prevent_generating_crash_assistant_app_logs")) {
                jvmArgs.add("-Dlog4j2.configurationFile=log4j2-no-logging.xml");
            }
            jvmArgs.add("-cp");
            jvmArgs.add(fullClassPath);
            jvmArgs.add("dev.kostromdan.mods.crash_assistant.app.class_loading.Boot");
            jvmArgs.add("--args-file");
            jvmArgs.add(argsFile.toString());

            ProcessBuilder crashAssistantAppProcessBuilder = new ProcessBuilder(jvmArgs);

            // Added by Embeddedt request. Since he is crashing very often for debugging and don't need window opening after crash.
            if ("true".equals(System.getenv("DisableEntirelyCrashAssistantModOnSystem"))) {
                LOGGER.error("Detected \"DisableEntirelyCrashAssistantModOnSystem\" env flag. Prevented start of Crash Assistant.");
                return;
            }

            Process crashAssistantAppProcess = crashAssistantAppProcessBuilder.start();
            ChildProcessLogger.captureOutput(crashAssistantAppProcess);
            JarInJarHelper.checkForIncompatibleMods(true);
            if (Startup.isMarkedForCrash()) {
                LOGGER.error("Game crash requested by startup scripts.");
                ProcessHelper.exitProcess(-1);
            }
            crashIfConfigured();
        } catch (Throwable e) {
            LOGGER.error("Error while launching GUI: ", e);
        }
    }

    public static void setupScripts() {
        if (CrashAssistantConfig.getBoolean("scripts.enabled") && CrashAssistantConfig.getBoolean("scripts.generate_scripts_folder_with_example")) {
            setupScriptDirectory(
                    Paths.get("config", "crash_assistant", "scripts", "log_analysis"),
                    "/META-INF/scripts/log_analysis/example.jexl",
                    "example.jexl"
            );

            setupScriptDirectory(
                    Paths.get("config", "crash_assistant", "scripts", "startup"),
                    "/META-INF/scripts/startup/example.jexl",
                    "example.jexl"
            );
        }
    }

    private static void setupScriptDirectory(Path scriptsDir, String exampleResourcePath, String exampleFileName) {
        try {
            Files.createDirectories(scriptsDir);
            try (Stream<Path> stream = Files.list(scriptsDir)) {
                if (!stream.findAny().isPresent()) {
                    Path exampleScript = scriptsDir.resolve(exampleFileName);
                    unzipFromJar(exampleResourcePath, exampleScript);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to setup scripts directory: " + scriptsDir, e);
        }
    }

    public static List<Path> getModJarPathsContainingPart(String part) {
        try {
            return Files.list(ModListUtils.MODS_FOLDER)
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

    public static boolean isLwjgl3ifyRelauncher() {
        List<Mod> mods = getModsContainingPart("lwjgl3ify");
        mods = mods.stream().filter(mod -> "lwjgl3ify".equals(mod.getModId())).collect(Collectors.toList());
        if (mods.isEmpty()) return false;

        try {
            Class<?> launchClass = Class.forName("net.minecraft.launchwrapper.Launch");
            java.lang.reflect.Field blackboardField = launchClass.getField("blackboard");
            java.util.Map<?, ?> blackboard = (java.util.Map<?, ?>) blackboardField.get(null);

            if (!Boolean.TRUE.equals(blackboard.get("lwjgl3ify:rfb-booted"))) {
                LOGGER.warn("Detected lwjgl3ify-relauncher env. Crash Assistant will start after relaunching with lwjgl3ify.");
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Error while checking lwjgl3ify-relauncher env.", e);
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
                String incompatibleMessage = crashAssistantString + " and " + incompatibleModsString + " are incompatible.";
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

    public static void crashIfConfigured() {
        if (CrashAssistantConfig.getBoolean("debug.crash_after_init")) {
            JarInJarHelper.LOGGER.error("Game crashed due to 'debug.crash_after_init' config option enabled in {}", CrashAssistantConfig.getConfigPath());
            ProcessHelper.exitProcess(-1);
        }
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
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
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
}
