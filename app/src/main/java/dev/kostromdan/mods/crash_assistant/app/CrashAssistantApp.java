package dev.kostromdan.mods.crash_assistant.app;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogsList;
import dev.kostromdan.mods.crash_assistant.app.utils.*;
import dev.kostromdan.mods.crash_assistant.app.utils.gpu.GPU;
import dev.kostromdan.mods.crash_assistant.app.utils.gpu.RendererType;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import org.apache.commons.jexl3.annotations.NoJexl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static dev.kostromdan.mods.crash_assistant.common_config.utils.MemoryUtils.*;

public class CrashAssistantApp {
    public static final Logger LOGGER = LogManager.getLogger(CrashAssistantApp.class);
    private static String customLatestLogPath = null;
    private static boolean GUIStartedLaunching = false;
    public static long GUIStartTime = -1;
    public static boolean GUIInitialisationFinished = false;
    public static String minecraftXms = null;
    public static String minecraftXmx = null;
    public static String systemRAM = null;
    public static String systemUsedRAMAtMinecraftLaunchMoment = null;
    public static String systemSwapSpace = null;
    public static String systemUsedSwapSpaceAtMinecraftLaunchMoment = null;
    public static String processor = null;
    public static boolean crashed = false;
    public static boolean crashed_with_report = false;
    public static boolean located_hs_err = false;
    public static String renderer = null;
    public static boolean gameLaunchedSuccessfully = false;
    public static boolean joinedWorldSuccessfully = false;
    public static boolean preventCrashAssistantWindow = false;
    public static long terminatedProcessesLocationEndTime = 0;

    @NoJexl
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Uncaught exception in \"{}\" thread:", thread.getName(), throwable);
        });
        LOGGER.info("CrashAssistantApp running: JVM args: {}", Boot.JVM_ARGS);
        LOGGER.info("CrashAssistantApp running: program args: {}", Boot.APP_ARGS);

        LOGGER.info("CrashAssistantApp running from: {}", Paths.get("").toAbsolutePath().toString());

        LOGGER.info("crashAssistantJarName: {}", Boot.crashAssistantModJarName);

        LOGGER.info("Parent PID: {}", Boot.parentPID);
        LOGGER.info("Parent started: {}", Boot.parentStarted);

        for (int i = 0; i < args.length; i++) {
            if ("-minecraftXms".equals(args[i]) && i + 1 < args.length) {
                minecraftXms = args[i + 1];
                LOGGER.info("minecraftXms: {}", minecraftXms);
            } else if ("-minecraftXmx".equals(args[i]) && i + 1 < args.length) {
                minecraftXmx = args[i + 1];
                LOGGER.info("minecraftXmx: {}", minecraftXmx);
            } else if ("-systemRAM".equals(args[i]) && i + 1 < args.length) {
                systemRAM = args[i + 1];
                LOGGER.info("systemRAM: {}", systemRAM);
            } else if ("-systemUsedRAMAtMinecraftLaunchMoment".equals(args[i]) && i + 1 < args.length) {
                systemUsedRAMAtMinecraftLaunchMoment = args[i + 1];
                LOGGER.info("systemUsedRAMAtMinecraftLaunchMoment: {}", systemUsedRAMAtMinecraftLaunchMoment);
            } else if ("-systemSwapSpace".equals(args[i]) && i + 1 < args.length) {
                systemSwapSpace = args[i + 1];
                LOGGER.info("systemSwapSpace: {}", systemSwapSpace);
            } else if ("-systemUsedSwapSpaceAtMinecraftLaunchMoment".equals(args[i]) && i + 1 < args.length) {
                systemUsedSwapSpaceAtMinecraftLaunchMoment = args[i + 1];
                LOGGER.info("systemUsedSwapSpaceAtMinecraftLaunchMoment: {}", systemUsedSwapSpaceAtMinecraftLaunchMoment);
            } else if ("-processor".equals(args[i]) && i + 1 < args.length) {
                processor = new String(Base64.getDecoder().decode(args[i + 1]), StandardCharsets.UTF_8);
                LOGGER.info("processor: {}", processor);
            } else if ("-platform".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.platform = Enum.valueOf(PlatformHelp.class, args[i + 1]);
                LOGGER.info("Platform: {}", PlatformHelp.platform);
            } else if ("-platformOwerridenData".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.platform.deserializePlatformData(args[i + 1]);
            } else if ("-loaderJarName".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.loaderJarName = args[i + 1];
                LOGGER.info("loaderJarName: {}", PlatformHelp.loaderJarName);
            } else if ("-minecraftVersion".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.minecraftVersion = args[i + 1];
                LOGGER.info("minecraftVersion: {}", PlatformHelp.minecraftVersion);
            } else if ("-childProcessesPIDs".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.childProcessesPIDs = new String(Base64.getDecoder().decode(args[i + 1]), StandardCharsets.UTF_8);
                LOGGER.info("childProcessesPIDs: {}", PlatformHelp.childProcessesPIDs);
            } else if ("-customLatestLogPath".equals(args[i]) && i + 1 < args.length) {
                customLatestLogPath = args[i + 1];
                LOGGER.info("customLatestLogPath: {}", customLatestLogPath);
            } else if ("-bootWarnings".equals(args[i]) && i + 1 < args.length) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(args[i + 1]), StandardCharsets.UTF_8);
                    LOGGER.info("Boot warnings: {}", decoded);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse bootWarnings", e);
                }
            } else if ("-warnsProcessOutput".equals(args[i]) && i + 1 < args.length) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(args[i + 1]), StandardCharsets.UTF_8);
                    LOGGER.info("Boot warnings process output:\n{}\n", decoded);
                } catch (Exception e) {
                    LOGGER.error("Failed to parse warnsProcessOutput", e);
                }
            }
        }
        LOGGER.info("Boot.getSerialisedGPUs():\n{}", Boot.getSerialisedGPUs());

        LOGGER.info("os.name: {}", PlatformHelp.OS);

        LOGGER.info("Java path: {}", JavaBinaryLocator.getJavaBinary());
        LOGGER.info("Java version: {}", PlatformHelp.javaVersion);

        LOGGER.info("Minecraft JVM args: {}", Boot.MINECRAFT_JVM_ARGS);
        LOGGER.info("Minecraft Launch Command: {}", Boot.MINECRAFT_LAUNCH_COMMAND);

        if (Boot.MINECRAFT_CLASS_PATH != null) {
            LOGGER.info("Minecraft classPath:\n\n\n{}\n\n", String.join("\n", Boot.MINECRAFT_CLASS_PATH.split(Pattern.quote(File.pathSeparator))));
        }


        String currentProcessData = Objects.toString(Boot.parentPID) + "_" + Boot.parentStarted;
        Path localFolder = Paths.get("local", "crash_assistant");
        Path currentProcessDataPath = localFolder.resolve(currentProcessData + ".info");
        try {
            Files.write(currentProcessDataPath, (ProcessHelper.getCurrentProcessId() + " : " + ProcessHelper.getCurrentProcessStartTime()).getBytes());
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(localFolder.resolve(currentProcessData + "_gpu_detect.cs"));
        } catch (IOException ignored) {
        }

        FileUtils.removeTmpFiles(localFolder);

        WinEventCleaner.cleanOldWinEventFiles();

        HsErrHelper.removeHsErrLog(Boot.parentPID);

        LOGGER.info("CrashAssistantApp started successfully. Waiting for PID " + Boot.parentPID + " to stop.");

        while (true) {
            try {
                if (Boot.parentStarted == -1 || Boot.parentStarted != ProcessHelper.getProcessStartTime(Boot.parentPID)) {
                    LOGGER.info("PID \"{}\" is not alive or reused by another process. Minecraft JVM appears to have stopped.", Boot.parentPID);
                    onMinecraftFinished();
                    return;
                }

                if (checkLoadingErrorScreen()) {
                    return;
                }

                checkRendererFile();

                System.gc();
                TimeUnit.SECONDS.sleep(1);

            } catch (Exception e) {
                LOGGER.error("Exception while awaiting Minecraft stop:", e);
                break;
            }
        }
    }

    private static boolean checkLoadingErrorScreen() {
        if (ProcessSignalIO.exists("loading_error_fml", Boot.parentPID)) {
            LOGGER.info("Detected FML error modloading screen.");
            if (CrashAssistantConfig.getBoolean("general.show_on_fml_error_screen")) {
                onMinecraftFinished();
            }
            return true;
        }
        return false;
    }

    @NoJexl
    public static void checkRendererFile() {
        if (renderer != null) return;
        if (Boot.getSerialisedGPUs() == null) return;
        Optional<String> potentialRenderer = ProcessSignalIO.get("renderer", Boot.parentPID);

        if (potentialRenderer.isPresent()) {
            try {
                renderer = potentialRenderer.get();
                String normalizedRenderer = removeSpacesAndLowerCase(renderer);
                LOGGER.info("Minecraft is running on renderer:\n{}", renderer);

                if (Boot.getSerialisedGPUs() != null) {
                    List<GPU> gpus = GPU.deserializeGPUs(Boot.getSerialisedGPUs());
                    List<String> dedicatedGpus = new ArrayList<>();
                    Optional<GPU> foundGPU = Optional.empty();
                    for (GPU gpu : gpus) {
                        if (gpu.getType() == RendererType.DEDICATED) {
                            dedicatedGpus.add(gpu.getName());
                        }
                        String normalizedGpuName = removeSpacesAndLowerCase(gpu.getName());
                        if (normalizedGpuName.contains(normalizedRenderer) || normalizedRenderer.contains(normalizedGpuName)) {
                            foundGPU = Optional.of(gpu);
                        }
                    }
                    if (foundGPU.isPresent() &&
                            foundGPU.get().getType() == RendererType.INTEGRATED &&
                            !dedicatedGpus.isEmpty()) {
                        LOGGER.warn("Detected Minecraft running on integrated GPU:\n" +
                                "{},\n" +
                                "while one or more dedicated exists:\n" +
                                "{}", foundGPU.get().getName(), String.join("\n", dedicatedGpus));
                        if (Objects.equals(CrashAssistantLocalConfig.get("integrated_gpu.dont_show_again"), true)) {
                            CrashAssistantApp.LOGGER.warn("integrated_gpu.dont_show_again is true. Prevented GUI warn.");
                            return;
                        }
                        try {
                            Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.IntegratedGPUWarning");
                            Method method = clazz.getMethod("show", String.class, List.class);
                            method.invoke(null, foundGPU.get().getName(), dedicatedGpus);
                        } catch (Exception e) {
                            LOGGER.error("Exception while showing IntegratedGPUWarning:", e);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Exception while analysis of current GPUs:", e);
                renderer = "UNDEFINED";
            }
        }
    }


    private static String removeSpacesAndLowerCase(String s) {
        return s.toLowerCase().replace(" ", "");
    }

    private static void onMinecraftFinished() {
        GUIStartTime = Instant.now().toEpochMilli();

        UUIDUtils.startCheck();

        new Thread(LanguageProvider::updateLang).start(); // Init lang async.

        LOGGER.info("System used RAM after Minecraft process finish moment: {}", formatMemorySize(getSystemUsedMemoryBytes()));
        LOGGER.info("System used Swap Space after Minecraft process finish moment: {}", formatMemorySize(getSystemUsedSwapBytes()));

        if (customLatestLogPath != null) {
            ModListUtils.MODS_FOLDER = Paths.get(customLatestLogPath).getParent().getParent().resolve("mods").resolve("fabric-" + PlatformHelp.minecraftVersion);
            LOGGER.info("ModListUtils.MODS_FOLDER: {}", ModListUtils.MODS_FOLDER);
        }

        LogsList.addIfExistsAndModified(new Log(LogType.LOG, customLatestLogPath == null ? Paths.get("logs", "latest.log") : Paths.get(customLatestLogPath)));
        LogsList.addIfExistsAndModified(new Log(LogType.DEBUG_LOG, Paths.get("logs", "debug.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.LOG, Paths.get("output-client.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LOG, Paths.get("output-server.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.BTW, Paths.get("BTWLog.txt")));

        locateAndAddHsErr();

        HashSet<Path> newCrashReports = CrashReportsHelper.getRelevantFiles(Paths.get("crash-reports"), path -> true);
        if (!newCrashReports.isEmpty()) {
            crashed = true;
            crashed_with_report = true;
            for (Path path : newCrashReports) {
                LogsList.addIfExistsAndModified(new Log(LogType.CRASH_REPORT, path));
            }
        }

        HashSet<Path> disconnectsClient = CrashReportsHelper.getRelevantFiles(Paths.get("minecraft", "debug"),
                path -> path.startsWith("disconnect-") && path.endsWith("-client.txt"));
        for (Path path : disconnectsClient) {
            LogsList.addIfExistsAndModified(new Log(LogType.DISCONNECT_CLIENT, path));
        }


        Log stderrLog = new Log(LogType.STDERR_STREAM, Paths.get("logs", "stderr_stream.log"));
        long logSizeBytes = stderrLog.getFile().length();
        LOGGER.info("stderr_stream.log size: {} bytes", logSizeBytes);
        if (Files.isRegularFile(stderrLog.getPath()) && logSizeBytes >= 400) {
            boolean add = true;
            if (logSizeBytes < 1048576) {
                stderrLog.getReader().readLogFileSafe();
                String logContents = stderrLog.getReader().getAllLinesString();
                if (logContents != null && !logContents.isEmpty()) {
                    add = Pattern.compile("^.*\\bat\\s+\\S+", Pattern.MULTILINE)
                            .matcher(logContents).find();
                }
            }
            if (add) LogsList.addIfExistsAndModified(stderrLog);
        }

        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "MinecraftLauncher: launcher_log.txt", Paths.get("launcher_log.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("logs", "launcher_log.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CurseForge: launcher_log.txt", Paths.get("../../Install", "launcher_log.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CurseForge: stdout-logs.txt", Paths.get("logs", "stdout-logs.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../logs", "ftb-app-electron.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../logs", "PrismLauncher-0.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "GDLauncher: main.log", Paths.get("../../../../", "main.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../", "MultiMC-0.log")));

        FileUtils.getModifiedFiles(Paths.get("../../launcher_logs"), ".log").forEach(path -> {
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path));
        });

        String appdata = System.getenv("APPDATA");

        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../logs", "atlauncher.log"))); // Mac atlauncher.log
        if (appdata != null) {
            // Windows
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get(appdata, "AtLauncher", "logs", "atlauncher.log")));

            FileUtils.getModifiedFiles(Paths.get(appdata, ".tlauncher", "logs", "tlauncher"), ".log").forEach(path -> {
                LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path));
            });
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "LegacyLauncher: launcher.log", Paths.get(appdata, ".tlauncher", "logs", "launcher.log")));
        }

        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../logs", "ElyPrismLauncher-0.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CrystalLauncher: latest.log", Paths.get("../../../../Crystal-Launcher/logs", "latest.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "KLauncher: game.log", Paths.get("logs/klauncher", "game.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "KLauncher: launcher.log", Paths.get("logs/klauncher", "launcher.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("sklauncher", "sklauncher_logs.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../", "PolyMC-0.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../logs", "PollyMC-0.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "Feather: latest.log", Paths.get("feather/logs", "latest.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "Lunar: ichor-boot.log", customLatestLogPath == null ? Paths.get("logs", "ichor-boot.log") : Paths.get(customLatestLogPath).getParent().resolve("ichor-boot.log")));
        if (FileUtils.folderNLevelsUpperNameContains(2, "technic")) {
            FileUtils.getModifiedFiles(Paths.get("../../logs"), ".log").forEach(path -> {
                LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path));
            });
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path applicationSupportPath = Paths.get(userHome, "Library", "Application Support");
            if (Files.exists(applicationSupportPath)) {
                // MacOS
                LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CurseForge: launcher_log.txt", Paths.get(applicationSupportPath.toString(), "minecraft", "launcher_log.txt")));

                FileUtils.getModifiedFiles(Paths.get(applicationSupportPath.toString(), "tlauncher", "logs", "tlauncher"), ".log").forEach(path -> {
                    LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path));
                });
                LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "LegacyLauncher: launcher.log", Paths.get(applicationSupportPath.toString(), "tlauncher", "logs", "launcher.log")));
            }
            // Linux
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CurseForge: launcher_log.txt", Paths.get(userHome, ".minecraft", "launcher_log.txt")));

            FileUtils.getModifiedFiles(Paths.get(userHome, ".tlauncher", "logs", "tlauncher"), ".log").forEach(path -> {
                LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path));
            });
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "LegacyLauncher: launcher.log", Paths.get(userHome, ".tlauncher", "logs", "launcher.log")));
        }


        LogsList.addIfExistsAndModified(new Log(LogType.MIXER_LOGGER, Paths.get("logs", "MixerLogger.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.KUBE_JS, "KubeJS: client.log", Paths.get("logs", "kubejs", "client.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.KUBE_JS, "KubeJS: server.log", Paths.get("logs", "kubejs", "server.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.KUBE_JS, "KubeJS: startup.log", Paths.get("logs", "kubejs", "startup.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.CRAFT_TWEAKER, Paths.get("logs", "crafttweaker.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.CRAFT_TWEAKER, Paths.get("crafttweaker.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.GROOVY, Paths.get("logs", "groovy.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.REI, Paths.get("logs", "rei.log")));
        Path reiIssuesPath = Paths.get("logs", "rei-issues.log");
        try {
            if (reiIssuesPath.toFile().exists() && Files.size(reiIssuesPath) != 0) {
                LogsList.addIfExistsAndModified(new Log(LogType.REI, reiIssuesPath));
            }
        } catch (IOException ignored) {

        }


        LogsList.addIfExistsAndModified(new Log(LogType.CRASH_ASSISTANT, Paths.get("logs", "crash_assistant", "crash_assistant_app.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.STARTUP_SCRIPTS, Paths.get("logs", "crash_assistant", "startup_scripts.log")));


        gameLaunchedSuccessfully = ProcessSignalIO.exists("successful_launch", Boot.parentPID);
        LOGGER.info("Reached first tick of TitleScreen: {}", gameLaunchedSuccessfully);

        joinedWorldSuccessfully = ProcessSignalIO.exists("joined_world", Boot.parentPID);
        LOGGER.info("Joined world successfully: {}", joinedWorldSuccessfully);

        preventCrashAssistantWindow = ProcessSignalIO.exists("prevent_crash_assistant_window", Boot.parentPID);
        LOGGER.info("Prevent Crash Assistant Window: {}", preventCrashAssistantWindow);

        boolean stopFunctionFired = ProcessSignalIO.exists("normal_stop", Boot.parentPID);
        if (!stopFunctionFired) crashed = true;
        LOGGER.info("stop() function of Minecraft fired: {}", stopFunctionFired);

        boolean closeFunctionFailed = ProcessSignalIO.exists("close_failed", Boot.parentPID);
        if (closeFunctionFailed) crashed = true;
        LOGGER.info("close() function of Minecraft failed: {}", closeFunctionFailed);

        boolean emergencySaveFired = ProcessSignalIO.exists("emergency_save", Boot.parentPID);
        if (emergencySaveFired) crashed = true;
        LOGGER.info("emergencySave() function of Minecraft fired: {}", emergencySaveFired);

        if (preventCrashAssistantWindow) crashed = false;

        LOGGER.info("isModpackCreator: {}", ModListDiff.isModpackCreator());
        LOGGER.info("isHelpLinkDefault: {}", PlatformHelp.isLinkDefault());
        LOGGER.info("helpLink: {}", PlatformHelp.getActualHelpLink());


        startLocatingTerminatedProcesses();

        if (crashed) {
            if (!crashed_with_report) {
                LOGGER.info("Seems like Minecraft crashed without any crash report. Starting Crash Assistant app.");

            } else {
                LOGGER.info("Seems like Minecraft crashed. Starting Crash Assistant app.");
            }
            onMinecraftCrashed();
        } else {
            LOGGER.info("Seems like Minecraft finished normally. Trying to locate terminated processes and Exiting Crash Assistant app.");
        }

    }

    private static void onMinecraftCrashed() {
        startApp();
    }


    private static void startApp() {
        GUIStartedLaunching = true;
        try {
            Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI");
            Constructor<?> constructor = clazz.getConstructor();
            constructor.newInstance();
        } catch (Exception e) {
            LOGGER.error("Exception while starting gui:", e);
        }
    }

    private static boolean locateAndAddHsErr() {
        if (located_hs_err) return false;
        Optional<Path> hsErrLog = HsErrHelper.locateHsErrLog(Boot.parentPID);
        if (hsErrLog.isPresent()) {
            crashed = true;
            crashed_with_report = true;
            located_hs_err = true;
            synchronized (KnownCrashReasonMessage.class) {
                LogsList.addIfExistsAndModified(new Log(LogType.HS_ERR, hsErrLog.get()));
            }
            return true;
        }
        return false;
    }

    private static void callUpdateLogsListInGUI() {
        try {
            Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI");
            Method method = clazz.getMethod("updateLogsListInGUI");
            method.invoke(null);
        } catch (Exception e) {
            LOGGER.error("Exception adding file to gui later:", e);
        }
    }

    private static void waitGuiInitialisationFinished() {
        long startTime = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() >= startTime + 7000) {
                LOGGER.error("Reached timeout while waiting for GUI initialisation finished.");
                System.exit(-1);
            }
            if (!GUIInitialisationFinished) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            return;
        }
    }

    private static void startLocatingTerminatedProcesses() {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            terminatedProcessesLocationEndTime = System.currentTimeMillis() + 7000;
            boolean firstIteration = true;
            while (System.currentTimeMillis() < terminatedProcessesLocationEndTime) {
                try {
                    Thread.sleep(firstIteration ? 3000 : 100);
                    if (firstIteration && locateAndAddHsErr()) {
                        LOGGER.info("Added hs_err log later.");
                        waitGuiInitialisationFinished();
                        callUpdateLogsListInGUI();
                    }
                    firstIteration = false;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Path terminatedProcessesPath = Paths.get(TerminatedProcessesFinder.getTerminatedByWinProcessLogs());
                if (terminatedProcessesPath.toFile().isFile()) {
                    LOGGER.info("Time to locate terminated process: " + (System.currentTimeMillis() - startTime));
                    synchronized (KnownCrashReasonMessage.class) {
                        LogsList.addIfExistsAndModified(new Log(LogType.WIN_EVENT, terminatedProcessesPath));
                    }
                    if (!GUIStartedLaunching) {
                        if (CrashAssistantConfig.getBoolean("general.win_event_is_crash")) onMinecraftCrashed();
                    } else {
                        waitGuiInitialisationFinished();
                        callUpdateLogsListInGUI();
                        terminatedProcessesLocationEndTime = System.currentTimeMillis();
                    }
                    break;
                }
            }
        }).start();
    }
}
