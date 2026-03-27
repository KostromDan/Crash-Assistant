package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.utils.ThemeUtils;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ModsSearcherStandaloneProcess {
    private static final Logger LOGGER = LogManager.getLogger(ModsSearcherStandaloneProcess.class);
    private static final Object PROCESS_LOCK = new Object();
    private static final Path WORKSPACE_ROOT = Paths.get("").toAbsolutePath().normalize();
    private static final String ARG_MODS_FOLDER = "--mods-folder";
    private static final String CHILD_LOG_PREFIX = "[ModsSearcherChild]";
    private static final String ERROR_DIALOG_TITLE = "Mods Searcher Process Error";
    private static final String ALREADY_RUNNING_MESSAGE = "Mods Searcher is already running in a separate process.";
    private static final String START_FAILURE_TEMPLATE = "Failed to start the standalone Mods Searcher process:\n%s";
    private static final String EXIT_FAILURE_TEMPLATE = "Mods Searcher process finished with exit code %s.\nSee the main log for details:\n%s";
    private static volatile Process runningProcess;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process process = runningProcess;
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }, "ModsSearcherChildShutdownHook"));
    }

    private ModsSearcherStandaloneProcess() {
    }

    public static void launch(JFrame parent) {
        synchronized (PROCESS_LOCK) {
            if (runningProcess != null && runningProcess.isAlive()) {
                showMessageDialog(parent,
                        ALREADY_RUNNING_MESSAGE,
                        ERROR_DIALOG_TITLE,
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            List<String> command = new ArrayList<>();
            command.add(JavaBinaryLocator.getJavaBinary());
            command.add("-Xms128m");
            command.add("-Xmx4g");
            command.add("-Dlog4j2.configurationFile=log4j2-console.xml");
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(ModsSearcherStandaloneProcess.class.getName());
            command.add(ARG_MODS_FOLDER);
            command.add(ModListUtils.MODS_FOLDER.toAbsolutePath().normalize().toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(WORKSPACE_ROOT.toFile());
            processBuilder.redirectErrorStream(true);

            try {
                Process process = processBuilder.start();
                runningProcess = process;
                startOutputForwarder(process);
                hideParent(parent);
                startMonitorThread(parent, process);
                LOGGER.info("Started standalone Mods Searcher process.");
            } catch (IOException e) {
                LOGGER.error("Failed to start standalone Mods Searcher process.", e);
                showMessageDialog(parent,
                        String.format(START_FAILURE_TEMPLATE, e.getMessage()),
                        ERROR_DIALOG_TITLE,
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOGGER.error("Uncaught exception in standalone Mods Searcher thread \"{}\":", thread.getName(), throwable)
        );

        try {
            applyArguments(args);
            ThemeUtils.ensureThemesApplied();
            SwingUtilities.invokeAndWait(() -> ModsSearcherAnalysisGUI.showDialog(null));
            System.exit(0);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            LOGGER.error("Standalone Mods Searcher failed to start UI.", cause);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Standalone Mods Searcher interrupted.", e);
            System.exit(1);
        } catch (Throwable e) {
            LOGGER.error("Standalone Mods Searcher failed.", e);
            System.exit(1);
        }
    }

    private static void applyArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (ARG_MODS_FOLDER.equals(args[i]) && i + 1 < args.length) {
                ModListUtils.MODS_FOLDER = Paths.get(args[i + 1]).toAbsolutePath().normalize();
                i++;
            }
        }
        LOGGER.info("Standalone Mods Searcher mods folder: {}", ModListUtils.MODS_FOLDER.toAbsolutePath().normalize());
        LOGGER.info("Standalone Mods Searcher working directory: {}", WORKSPACE_ROOT);
    }

    private static void startOutputForwarder(Process process) {
        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("{} {}", CHILD_LOG_PREFIX, line);
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    LOGGER.warn("Failed to read Mods Searcher child output.", e);
                }
            }
        }, "ModsSearcherChildOutputForwarder");
        outputThread.setDaemon(true);
        outputThread.start();
    }

    private static void startMonitorThread(JFrame parent, Process process) {
        Thread monitorThread = new Thread(() -> {
            int exitCode = 1;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while waiting for standalone Mods Searcher process.", e);
            } finally {
                synchronized (PROCESS_LOCK) {
                    if (runningProcess == process) {
                        runningProcess = null;
                    }
                }
            }

            final int finalExitCode = exitCode;
            SwingUtilities.invokeLater(() -> {
                restoreParent(parent);
                if (finalExitCode != 0) {
                    LOGGER.error("Standalone Mods Searcher process finished with exit code {}.", finalExitCode);
                    showMessageDialog(parent,
                            String.format(EXIT_FAILURE_TEMPLATE,
                                    String.valueOf(finalExitCode),
                                    Paths.get("logs", "crash_assistant", "crash_assistant_app.log").toAbsolutePath().normalize().toString()),
                            ERROR_DIALOG_TITLE,
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    LOGGER.info("Standalone Mods Searcher process finished successfully.");
                }
            });
        }, "ModsSearcherChildMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private static void hideParent(JFrame parent) {
        if (parent == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            parent.setVisible(false);
        } else {
            SwingUtilities.invokeLater(() -> parent.setVisible(false));
        }
    }

    private static void restoreParent(JFrame parent) {
        if (parent == null || !parent.isDisplayable()) {
            return;
        }
        parent.setVisible(true);
        parent.toFront();
        parent.requestFocus();
    }

    private static void showMessageDialog(JFrame parent, String message, String title, int messageType) {
        if (SwingUtilities.isEventDispatchThread()) {
            JOptionPane.showMessageDialog(parent, message, title, messageType);
        } else {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, message, title, messageType));
        }
    }
}
