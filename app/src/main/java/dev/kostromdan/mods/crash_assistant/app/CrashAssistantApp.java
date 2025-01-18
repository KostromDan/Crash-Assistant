package dev.kostromdan.mods.crash_assistant.app;

import dev.kostromdan.mods.crash_assistant.app.utils.CrashReportsHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.FileUtils;
import dev.kostromdan.mods.crash_assistant.app.utils.HsErrHelper;
import dev.kostromdan.mods.crash_assistant.app.utils.PIDHelper;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CrashAssistantApp {
    public static final Logger LOGGER = LogManager.getLogger(CrashAssistantApp.class);
    public static long parentPID;
    public static long parentStarted;
    public static boolean crashed_with_report = false;
    public static int launcherLogsCount = 0;


    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Uncaught exception in \"{}\" thread:", thread.getName(), throwable);
        });
        LOGGER.info("CrashAssistantApp running: JVM args: {}", ManagementFactory.getRuntimeMXBean().getInputArguments());
        LOGGER.info("CrashAssistantApp running: program args: [{}]", String.join(", ", args));

        parentPID = -1;
        for (int i = 0; i < args.length; i++) {
            if ("-parentPID".equals(args[i]) && i + 1 < args.length) {
                parentPID = Long.parseLong(args[i + 1]);
                LOGGER.info("Parent PID: {}", parentPID);
            }
        }

        parentStarted = 0;
        if (parentPID != -1) {
            Optional<Instant> time = PIDHelper.findProcessByPID(parentPID).info().startInstant();
            time.ifPresent(instant -> parentStarted = instant.toEpochMilli());
        }

        FileUtils.removeTmpFiles(Paths.get("local", "crash_assistant"));
        CrashReportsHelper.cacheKnownCrashReports();
        HsErrHelper.removeHsErrLog(parentPID);

        LOGGER.info("CrashAssistantApp started successfully. Waiting for PID " + parentPID + " to stop.");

        while (true) {
            try {
                if (!PIDHelper.isProcessAlive(parentPID)) {
                    LOGGER.info("PID \"{}\" is not alive. Minecraft JVM appears to have stopped.", parentPID);
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
        boolean crashed = false;
        LinkedHashMap<String, Path> availableLogs = new LinkedHashMap<>();

        FileUtils.addIfExistsAndModified(availableLogs, Paths.get("logs", "latest.log"));
        FileUtils.addIfExistsAndModified(availableLogs, Paths.get("logs", "debug.log"));

        Optional<Path> hsErrLog = HsErrHelper.locateHsErrLog(parentPID);
        if (hsErrLog.isPresent()) {
            crashed = true;
            crashed_with_report = true;
            availableLogs.put(hsErrLog.get().getFileName().toString(), hsErrLog.get());
        }

        HashSet<Path> newCrashReports = CrashReportsHelper.scanForNewCrashReports();
        if (!newCrashReports.isEmpty()) {
            crashed = true;
            crashed_with_report = true;
            for (Path path : newCrashReports) {
                availableLogs.put(path.getFileName().toString(), path);
            }
        }


        launcherLogsCount = availableLogs.size();
        FileUtils.addIfExistsAndModified(availableLogs, "MinecraftLauncher: launcher_log.txt", Paths.get("launcher_log.txt"));
        FileUtils.addIfExistsAndModified(availableLogs, "CurseForge: launcher_log.txt", Paths.get("../../Install", "launcher_log.txt"));
        FileUtils.addIfExistsAndModified(availableLogs, "FTB Electron App: ftb-app-electron.log", Paths.get("../../logs", "ftb-app-electron.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "Prism Launcher: PrismLauncher-0.log", Paths.get("../../../logs", "PrismLauncher-0.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "GDLauncher: main.log", Paths.get("../../../../", "main.log"));
        FileUtils.addIfExistsAndModified(availableLogs, "MultiMC: MultiMC-0.log", Paths.get("../../../", "MultiMC-0.log"));

        FileUtils.getModifiedFiles(Paths.get("../../launcher_logs"), ".log").forEach(path -> {
            FileUtils.addIfExistsAndModified(availableLogs, "Modrinth: " + path.getFileName().toString(), path);
        });

        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            FileUtils.addIfExistsAndModified(availableLogs, "AtLauncher: atlauncher.log", Paths.get(appdata, "AtLauncher", "logs", "atlauncher.log"));

            FileUtils.getModifiedFiles(Paths.get(appdata, ".tlauncher", "logs", "tlauncher"), ".log").forEach(path -> {
                FileUtils.addIfExistsAndModified(availableLogs, "TLauncher: " + path.getFileName().toString(), path); // To notify modpack creators about TLauncher usage.
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


        FileUtils.addIfExistsAndModified(availableLogs, "CrashAssistant: latest.log", Paths.get("local", "crash_assistant", "logs", "latest.log"));


        String normalStopFileName = "normal_stop_pid" + parentPID + ".tmp";
        Path normalStopFilePath = Paths.get("local", "crash_assistant", normalStopFileName);
        if (!(Files.exists(normalStopFilePath) && Files.isRegularFile(normalStopFilePath))) {
            crashed = true;
        }

        if (crashed) {
            if (!crashed_with_report) {
                LOGGER.info("Seems like Minecraft crashed without any crash report. Starting Crash Assistant app.");

            } else {
                LOGGER.info("Seems like Minecraft crashed. Starting Crash Assistant app.");
            }
            onMinecraftCrashed(availableLogs);
        } else {
            LOGGER.info("Seems like Minecraft finished normally. Exiting Crash Assistant app.");
        }

    }

    private static void onMinecraftCrashed(Map<String, Path> availableLogs) {
        startApp(availableLogs);
    }


    public static void startApp(Map<String, Path> availableLogs) {
        try {
            Class<?> clazz = Class.forName("dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI");
            Constructor<?> constructor = clazz.getConstructor(Map.class);
            constructor.newInstance(availableLogs);
        } catch (Exception e) {
            LOGGER.error("Exception while starting gui:", e);
        }
    }
}