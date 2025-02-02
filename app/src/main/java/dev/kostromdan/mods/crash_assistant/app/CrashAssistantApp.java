package dev.kostromdan.mods.crash_assistant.app;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.utils.*;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.platform.PlatformHelp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Constructor;
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
    public static boolean crashed_with_report = false;
    public static int launcherLogsCount = 0;


    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Uncaught exception in \"{}\" thread:", thread.getName(), throwable);
        });
        LOGGER.info("CrashAssistantApp running: JVM args: {}", Boot.JVM_ARGS);
        LOGGER.info("CrashAssistantApp running: program args: {}", Boot.APP_ARGS);

        parentPID = -1;
        for (int i = 0; i < args.length; i++) {
            if ("-parentPID".equals(args[i]) && i + 1 < args.length) {
                parentPID = Long.parseLong(args[i + 1]);
                LOGGER.info("Parent PID: {}", parentPID);
            } else if ("-platform".equals(args[i]) && i + 1 < args.length) {
                PlatformHelp.platform = Enum.valueOf(PlatformHelp.class, args[i + 1]);
                LOGGER.info("Platform: {}", PlatformHelp.platform);
            }
        }

        parentStarted = ProcessHelper.getStartTime(parentPID);

        String currentProcessData = Objects.toString(parentPID) + "_" + Objects.toString(Instant.ofEpochMilli(parentStarted).getEpochSecond());
        Path currentProcessDataPath = Paths.get("local", "crash_assistant", currentProcessData + ".info");
        try {
            Files.write(currentProcessDataPath, Long.toString(ProcessHandle.current().pid()).getBytes());
        } catch (IOException ignored) {
        }

        FileUtils.removeTmpFiles(Paths.get("local", "crash_assistant"));
        FileUtils.removeOldLogsFolder();

        CrashReportsHelper.cacheKnownCrashReports();
        HsErrHelper.removeHsErrLog(parentPID);

        LOGGER.info("CrashAssistantApp started successfully. Waiting for PID " + parentPID + " to stop.");

        while (true) {
            try {
                if (parentStarted == -1 || parentStarted != ProcessHelper.getStartTime(parentPID)) {
                    LOGGER.info("PID \"{}\" is not alive or reused by another process. Minecraft JVM appears to have stopped.", parentPID);
                    onMinecraftFinished();
                    return;
                }

                if (checkLoadingErrorScreen()) {
                    return;
                }

                System.gc();
                TimeUnit.SECONDS.sleep(1);

            } catch (Exception e) {
                LOGGER.error("Exception while awaiting Minecraft stop:", e);
                break;
            }
        }
    }

    private static boolean checkLoadingErrorScreen() {
        Path loadingErrorFML = Paths.get("local", "crash_assistant", "loading_error_fml" + parentPID + ".tmp");

        if (loadingErrorFML.toFile().exists()) {
            LOGGER.info("Detected FML error modloading screen.");
            if (CrashAssistantConfig.getBoolean("general.show_on_fml_error_screen")) {
                onMinecraftFinished();
            }
            return true;
        }
        return false;
    }

    private static void onMinecraftFinished() {
        GUIStartTime = Instant.now().toEpochMilli();
        boolean crashed = false;
        LinkedHashMap<String, Path> availableLogs = new LinkedHashMap<>();

        FileUtils.addIfExistsAndModified(availableLogs, Paths.get("logs", "latest.log"));
        FileUtils.addIfExistsAndModified(availableLogs, Paths.get("logs", "debug.log"));

        Optional<Path> hsErrLog = HsErrHelper.locateHsErrLog(parentPID);
        if (hsErrLog.isPresent()) {
            crashed = true;
            crashed_with_report = true;
            FileUtils.addIfExistsAndModified(availableLogs, hsErrLog.get());
        }

        HashSet<Path> newCrashReports = CrashReportsHelper.scanForNewCrashReports();
        if (!newCrashReports.isEmpty()) {
            crashed = true;
            crashed_with_report = true;
            for (Path path : newCrashReports) {
                FileUtils.addIfExistsAndModified(availableLogs, path);
            }
        }


        launcherLogsCount = availableLogs.size();
        FileUtils.addIfExistsAndModified(availableLogs, "MinecraftLauncher: launcher_log.txt", Paths.get("launcher_log.txt"));
        FileUtils.addIfExistsAndModified(availableLogs, "CurseForge: launcher_log.txt", Paths.get("../../Install", "launcher_log.txt"));
        FileUtils.addIfExistsAndModified(availableLogs, "ftb-app-electron.log", Paths.get("../../logs", "ftb-app-electron.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "PrismLauncher-0.log", Paths.get("../../../logs", "PrismLauncher-0.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "GDLauncher: main.log", Paths.get("../../../../", "main.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "MultiMC-0.log", Paths.get("../../../", "MultiMC-0.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "PolyMC-0.log", Paths.get("../../../", "PolyMC-0.log"));

        FileUtils.getModifiedFiles(Paths.get("../../launcher_logs"), ".log").forEach(path -> {
            FileUtils.addIfExistsAndModified(availableLogs, path.getFileName().toString(), path);
        });

        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            FileUtils.addIfExistsAndModified(availableLogs, "atlauncher.log", Paths.get(appdata, "AtLauncher", "logs", "atlauncher.log"));

            FileUtils.getModifiedFiles(Paths.get(appdata, ".tlauncher", "logs", "tlauncher"), ".log").forEach(path -> {
                FileUtils.addIfExistsAndModified(availableLogs, path.getFileName().toString(), path); // To notify modpack creators about TLauncher usage.
            });
        }
        launcherLogsCount = availableLogs.size() - launcherLogsCount;


        FileUtils.addIfExistsAndModified(availableLogs, "KubeJS: client.log", Paths.get("logs", "kubejs", "client.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "KubeJS: server.log", Paths.get("logs", "kubejs", "server.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "KubeJS: startup.log", Paths.get("logs", "kubejs", "startup.log"));

        FileUtils.addIfExistsAndModified(availableLogs, Paths.get("logs", "crafttweaker.log"));
        FileUtils.addIfExistsAndModified(availableLogs, Paths.get("logs", "rei.log"));
        Path reiIssuesPath = Paths.get("logs", "rei-issues.log");
        try {
            if (reiIssuesPath.toFile().exists() && Files.size(reiIssuesPath) != 0) {
                FileUtils.addIfExistsAndModified(availableLogs, reiIssuesPath);
            }
        } catch (IOException ignored) {

        }


        FileUtils.addIfExistsAndModified(availableLogs, "CrashAssistant: latest.log", Paths.get("logs", "crash_assistant", "latest.log"));


        String normalStopFileName = "normal_stop_pid" + parentPID + ".tmp";
        Path normalStopFilePath = Paths.get("local", "crash_assistant", normalStopFileName);
        if (!(Files.exists(normalStopFilePath) && Files.isRegularFile(normalStopFilePath))) {
            crashed = true;
        }

        startLocatingTerminatedProcesses(availableLogs);

        if (crashed) {
            if (!crashed_with_report) {
                LOGGER.info("Seems like Minecraft crashed without any crash report. Starting Crash Assistant app.");

            } else {
                LOGGER.info("Seems like Minecraft crashed. Starting Crash Assistant app.");
            }
            onMinecraftCrashed(availableLogs);
        } else {
            LOGGER.info("Seems like Minecraft finished normally. Trying to locate terminated processes and Exiting Crash Assistant app.");
        }

    }

    private static void onMinecraftCrashed(Map<String, Path> availableLogs) {
        startApp(availableLogs);
    }


    public static void startApp(Map<String, Path> availableLogs) {
        GUIStartedLaunching = true;
        try {
            Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI");
            Constructor<?> constructor = clazz.getConstructor(Map.class);
            constructor.newInstance(availableLogs);
        } catch (Exception e) {
            LOGGER.error("Exception while starting gui:", e);
        }
    }

    public static void startLocatingTerminatedProcesses(Map<String, Path> availableLogs) {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() < startTime + 5000) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Path terminatedProcessesPath = Paths.get(TerminatedProcessesFinder.getTerminatedByWinProcessLogs());
                if (terminatedProcessesPath.toFile().isFile()) {
                    LOGGER.info("Time to locate terminated process: " + (System.currentTimeMillis() - startTime));
                    if (!GUIStartedLaunching) {
                        FileUtils.addIfExistsAndModified(availableLogs, terminatedProcessesPath);
                        onMinecraftCrashed(availableLogs);
                    } else {
                        startTime = System.currentTimeMillis();
                        while (true) {
                            if (System.currentTimeMillis() >= startTime + 5000) System.exit(-1);
                            if (!GUIInitialisationFinished) {
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                continue;
                            }
                            SwingUtilities.invokeLater(() -> {
                                CrashAssistantGUI.fileListPanel.addFile(terminatedProcessesPath.getFileName().toString(), terminatedProcessesPath);
                                CrashAssistantGUI.resize();
                            });
                            break;
                        }
                    }
                    break;
                }
            }
        }).start();
    }
}