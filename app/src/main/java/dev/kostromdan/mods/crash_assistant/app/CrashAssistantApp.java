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
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

public class CrashAssistantApp {
    public static final Logger LOGGER = LogManager.getLogger(CrashAssistantApp.class);
    public static long GUIStartTime = -1;
    public static boolean GUIStartedLaunching = false;
    public static boolean GUIInitialisationFinished = false;
    public static long parentPID;
    public static long parentStarted;
    public static String parentXms = null;
    public static String parentXmx = null;
    public static String systemRAM = null;
    public static String processor = null;
    public static boolean crashed_with_report = false;
    public static String crashAssistantJarName = Boot.crashAssistantModJarPath == null ? null : Paths.get(Boot.crashAssistantModJarPath).getFileName().toString();
    public static String renderer = null;
    public static boolean gameLaunchedSuccessfully = false;
    public static boolean joinedWorldSuccessfully = false;
    public static boolean stopFunctionFired = false;
    public static long terminatedProcessesLocationEndTime = 0;


    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Uncaught exception in \"{}\" thread:", thread.getName(), throwable);
        });
        LOGGER.info("CrashAssistantApp running: JVM args: {}", Boot.JVM_ARGS);
        LOGGER.info("CrashAssistantApp running: program args: {}", Boot.APP_ARGS);

        LOGGER.info("CrashAssistantApp running from: {}", Paths.get("").toAbsolutePath().toString());

        parentPID = -1;
        parentStarted = -1;
        for (int i = 0; i < args.length; i++) {
            if ("-parentPID".equals(args[i]) && i + 1 < args.length) {
                parentPID = Long.parseLong(args[i + 1]);
                LOGGER.info("Parent PID: {}", parentPID);
            } else if ("-parentStarted".equals(args[i]) && i + 1 < args.length) {
                parentStarted = Long.parseLong(args[i + 1]);
                LOGGER.info("Parent started: {}", parentStarted);
            } else if ("-parentXms".equals(args[i]) && i + 1 < args.length) {
                parentXms = args[i + 1];
                LOGGER.info("parentXms: {}", parentXms);
            } else if ("-parentXmx".equals(args[i]) && i + 1 < args.length) {
                parentXmx = args[i + 1];
                LOGGER.info("parentXmx: {}", parentXmx);
            } else if ("-systemRAM".equals(args[i]) && i + 1 < args.length) {
                systemRAM = args[i + 1];
                LOGGER.info("systemRAM: {}", systemRAM);
            } else if ("-processor".equals(args[i]) && i + 1 < args.length) {
                processor = args[i + 1];
                LOGGER.info("processor: {}", processor);
            } else if ("-platform".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.platform = Enum.valueOf(PlatformHelp.class, args[i + 1]);
                LOGGER.info("Platform: {}", PlatformHelp.platform);
            } else if ("-loaderJarName".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.loaderJarName = args[i + 1];
                LOGGER.info("loaderJarName: {}", PlatformHelp.loaderJarName);
            } else if ("-minecraftVersion".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.minecraftVersion = args[i + 1];
                LOGGER.info("minecraftVersion: {}", PlatformHelp.minecraftVersion);
            } else if ("-childProcessesPIDs".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.childProcessesPIDs = new String(Base64.getDecoder().decode(args[i + 1]), StandardCharsets.UTF_8);
                LOGGER.info("childProcessesPIDs: {}", PlatformHelp.childProcessesPIDs);
            }
        }
        LOGGER.info("crashAssistantJarName: {}", crashAssistantJarName);

        LOGGER.info("os.name: {}", PlatformHelp.OS);

        LOGGER.info("Java path: {}", JavaBinaryLocator.getJavaBinary());
        LOGGER.info("Java version: {}", PlatformHelp.javaVersion);

        LOGGER.info("Boot.serialisedGPUs:\n{}", Boot.serialisedGPUs);

        String currentProcessData = Objects.toString(parentPID) + "_" + parentStarted;
        Path currentProcessDataPath = Paths.get("local", "crash_assistant", currentProcessData + ".info");
        try {
            Files.write(currentProcessDataPath, Long.toString(ProcessHelper.getCurrentProcessId()).getBytes());
        } catch (IOException ignored) {
        }

        FileUtils.removeTmpFiles(Paths.get("local", "crash_assistant"));
        FileUtils.removeOldLogsFolder();

        WinEventCleaner.cleanOldWinEventFiles();

        HsErrHelper.removeHsErrLog(parentPID);

        LOGGER.info("CrashAssistantApp started successfully. Waiting for PID " + parentPID + " to stop.");

        while (true) {
            try {
                if (parentStarted == -1 || parentStarted != ProcessHelper.getProcessStartTime(parentPID)) {
                    LOGGER.info("PID \"{}\" is not alive or reused by another process. Minecraft JVM appears to have stopped.", parentPID);
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
        if (ProcessSignalIO.exists("loading_error_fml", parentPID)) {
            LOGGER.info("Detected FML error modloading screen.");
            if (CrashAssistantConfig.getBoolean("general.show_on_fml_error_screen")) {
                onMinecraftFinished();
            }
            return true;
        }
        return false;
    }

    private static void checkRendererFile() {
        if (renderer != null) return;
        if (Boot.serialisedGPUs == null) return;
        Optional<String> potentialRenderer = ProcessSignalIO.get("renderer", parentPID);

        if (potentialRenderer.isPresent()) {
            try {
                renderer = potentialRenderer.get();
                String normalizedRenderer = removeSpacesAndLowerCase(renderer);
                LOGGER.info("Minecraft is running on renderer:\n{}", renderer);

                if (Boot.serialisedGPUs != null) {
                    List<GPU> gpus = GPU.deserializeGPUs(Boot.serialisedGPUs);
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
                            Method method = clazz.getMethod("showIfNotDisabled", String.class, List.class);
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

        new Thread(LanguageProvider::updateLang).start(); // Init lang async.

        boolean crashed = false;

        LogsList.addIfExistsAndModified(new Log(LogType.LOG, Paths.get("logs", "latest.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.DEBUG_LOG, Paths.get("logs", "debug.log")));

        Optional<Path> hsErrLog = HsErrHelper.locateHsErrLog(parentPID);
        if (hsErrLog.isPresent()) {
            crashed = true;
            crashed_with_report = true;
            LogsList.addIfExistsAndModified(new Log(LogType.HS_ERR, hsErrLog.get()));
        }

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


        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "MinecraftLauncher: launcher_log.txt", Paths.get("launcher_log.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CurseForge: launcher_log.txt", Paths.get("../../Install", "launcher_log.txt")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../logs", "ftb-app-electron.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../logs", "PrismLauncher-0.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "GDLauncher: main.log", Paths.get("../../../../", "main.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../", "MultiMC-0.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../../", "PolyMC-0.log")));

        FileUtils.getModifiedFiles(Paths.get("../../launcher_logs"), ".log").forEach(path -> {
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path));
        });

        String appdata = System.getenv("APPDATA");

        // Mac atlauncher.log
        LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get("../../logs", "atlauncher.log")));
        if (appdata != null) {
            // Windows atlauncher.log
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, Paths.get(appdata, "AtLauncher", "logs", "atlauncher.log")));

            FileUtils.getModifiedFiles(Paths.get(appdata, ".tlauncher", "logs", "tlauncher"), ".log").forEach(path -> {
                LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, path)); // To notify modpack creators about TLauncher usage.
            });
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !LogsList.isLauncherLogExist()) {
            // MacOS CurseForge: launcher_log.txt
            LogsList.addIfExistsAndModified(new Log(LogType.LAUNCHER_LOG, "CurseForge: launcher_log.txt", Paths.get(userHome, "Library", "Application Support", "minecraft", "launcher_log.txt")));
        }


        LogsList.addIfExistsAndModified(new Log(LogType.KUBE_JS, "KubeJS: client.log", Paths.get("logs", "kubejs", "client.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.KUBE_JS, "KubeJS: server.log", Paths.get("logs", "kubejs", "server.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.KUBE_JS, "KubeJS: startup.log", Paths.get("logs", "kubejs", "startup.log")));

        LogsList.addIfExistsAndModified(new Log(LogType.CRAFT_TWEAKER, Paths.get("logs", "crafttweaker.log")));
        LogsList.addIfExistsAndModified(new Log(LogType.REI, Paths.get("logs", "rei.log")));
        Path reiIssuesPath = Paths.get("logs", "rei-issues.log");
        try {
            if (reiIssuesPath.toFile().exists() && Files.size(reiIssuesPath) != 0) {
                LogsList.addIfExistsAndModified(new Log(LogType.REI, reiIssuesPath));
            }
        } catch (IOException ignored) {

        }


        LogsList.addIfExistsAndModified(new Log(LogType.CRASH_ASSISTANT, Paths.get("logs", "crash_assistant", "crash_assistant_app.log")));

        stopFunctionFired = ProcessSignalIO.exists("normal_stop", parentPID);
        if (!stopFunctionFired) {
            crashed = true;
        }

        gameLaunchedSuccessfully = ProcessSignalIO.exists("successful_launch", parentPID);
        LOGGER.info("Reached first tick of TitleScreen: {}", gameLaunchedSuccessfully);

        joinedWorldSuccessfully = ProcessSignalIO.exists("joined_world", parentPID);
        LOGGER.info("Joined world successfully: {}", joinedWorldSuccessfully);

        LOGGER.info("Stop function of Minecraft fired: {}", stopFunctionFired);


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


    public static void startApp() {
        GUIStartedLaunching = true;
        try {
            Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI");
            Constructor<?> constructor = clazz.getConstructor();
            constructor.newInstance();
        } catch (Exception e) {
            LOGGER.error("Exception while starting gui:", e);
        }
    }

    public static void startLocatingTerminatedProcesses() {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            terminatedProcessesLocationEndTime = System.currentTimeMillis() + 7000;
            boolean firstIteration = true;
            while (System.currentTimeMillis() < terminatedProcessesLocationEndTime) {
                try {
                    Thread.sleep(firstIteration ? 3000 : 100);
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
                        onMinecraftCrashed();
                    } else {
                        startTime = System.currentTimeMillis();
                        while (true) {
                            if (System.currentTimeMillis() >= startTime + 7000) System.exit(-1);
                            if (!GUIInitialisationFinished) {
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                continue;
                            }
                            try {
                                Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI");
                                Method method = clazz.getMethod("updateLogsListInGUI");
                                method.invoke(null);
                            } catch (Exception e) {
                                LOGGER.error("Exception adding file to gui later:", e);
                            }
                            terminatedProcessesLocationEndTime = System.currentTimeMillis();
                            break;
                        }
                    }
                    break;
                }
            }
        }).start();
    }
}
