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
import java.util.Base64;
import java.util.List;

public final class ModsSearcherStandaloneProcess {
    private static final Logger LOGGER = LogManager.getLogger(ModsSearcherStandaloneProcess.class);
    private static final Object PROCESS_LOCK = new Object();
    private static final Path WORKSPACE_ROOT = Paths.get("").toAbsolutePath().normalize();
    private static final String ARG_MODS_FOLDER = "--mods-folder";
    private static final String ARG_AUTO_START = "--auto-start";
    private static final String ARG_PATTERNS_BASE64 = "--patterns-base64";
    private static final String ARG_INCLUDE_JAR_IN_JAR = "--include-jar-in-jar";
    private static final String ARG_CASE_INSENSITIVE = "--case-insensitive";
    private static final String ARG_REGEX = "--regex";
    private static final String ARG_CHECK_FILE_NAMES = "--check-file-names";
    private static final String ARG_SEARCH_INSIDE_ARCHIVES = "--search-inside-archives";
    private static final String ARG_SCOPE = "--scope";
    private static final String ARG_CUSTOM_PATH_BASE64 = "--custom-path-base64";
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
        launch(parent, null);
    }

    public static void launch(JFrame parent, SearchRequest searchRequest) {
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
            command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-Dlog4j2.configurationFile=log4j2-console.xml");
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(ModsSearcherStandaloneProcess.class.getName());
            command.add(ARG_MODS_FOLDER);
            command.add(ModListUtils.MODS_FOLDER.toAbsolutePath().normalize().toString());
            if (searchRequest != null) {
                command.add(ARG_AUTO_START);
                command.add(ARG_PATTERNS_BASE64);
                command.add(encodeBase64(searchRequest.rawPatterns));
                command.add(ARG_INCLUDE_JAR_IN_JAR);
                command.add(String.valueOf(searchRequest.includeJarInJar));
                command.add(ARG_CASE_INSENSITIVE);
                command.add(String.valueOf(searchRequest.caseInsensitive));
                command.add(ARG_REGEX);
                command.add(String.valueOf(searchRequest.regex));
                command.add(ARG_CHECK_FILE_NAMES);
                command.add(String.valueOf(searchRequest.checkFileNames));
                command.add(ARG_SEARCH_INSIDE_ARCHIVES);
                command.add(String.valueOf(searchRequest.searchInsideArchives));
                command.add(ARG_SCOPE);
                command.add(searchRequest.scopeId);
                command.add(ARG_CUSTOM_PATH_BASE64);
                command.add(encodeBase64(searchRequest.customPathText));
            }

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
            ChildLaunchArguments launchArguments = parseArguments(args);
            ThemeUtils.ensureThemesApplied();
            SwingUtilities.invokeAndWait(() -> {
                if (launchArguments.searchRequest != null) {
                    ModsSearcherAnalysisGUI.startImmediateSearch(
                            null,
                            launchArguments.searchRequest.rawPatterns,
                            launchArguments.searchRequest.includeJarInJar,
                            launchArguments.searchRequest.caseInsensitive,
                            launchArguments.searchRequest.regex,
                            launchArguments.searchRequest.checkFileNames,
                            launchArguments.searchRequest.searchInsideArchives,
                            launchArguments.searchRequest.scopeId,
                            launchArguments.searchRequest.customPathText
                    );
                } else {
                    ModsSearcherAnalysisGUI.showDialog(null);
                }
            });
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

    private static ChildLaunchArguments parseArguments(String[] args) {
        SearchRequest searchRequest = null;
        boolean autoStart = false;
        String rawPatterns = "";
        boolean includeJarInJar = true;
        boolean caseInsensitive = false;
        boolean regex = false;
        boolean checkFileNames = true;
        boolean searchInsideArchives = true;
        String scopeId = "root";
        String customPathText = "";

        for (int i = 0; i < args.length; i++) {
            if (ARG_MODS_FOLDER.equals(args[i]) && i + 1 < args.length) {
                ModListUtils.MODS_FOLDER = Paths.get(args[i + 1]).toAbsolutePath().normalize();
                i++;
            } else if (ARG_AUTO_START.equals(args[i])) {
                autoStart = true;
            } else if (ARG_PATTERNS_BASE64.equals(args[i]) && i + 1 < args.length) {
                rawPatterns = decodeBase64(args[i + 1]);
                i++;
            } else if (ARG_INCLUDE_JAR_IN_JAR.equals(args[i]) && i + 1 < args.length) {
                includeJarInJar = Boolean.parseBoolean(args[i + 1]);
                i++;
            } else if (ARG_CASE_INSENSITIVE.equals(args[i]) && i + 1 < args.length) {
                caseInsensitive = Boolean.parseBoolean(args[i + 1]);
                i++;
            } else if (ARG_REGEX.equals(args[i]) && i + 1 < args.length) {
                regex = Boolean.parseBoolean(args[i + 1]);
                i++;
            } else if (ARG_CHECK_FILE_NAMES.equals(args[i]) && i + 1 < args.length) {
                checkFileNames = Boolean.parseBoolean(args[i + 1]);
                i++;
            } else if (ARG_SEARCH_INSIDE_ARCHIVES.equals(args[i]) && i + 1 < args.length) {
                searchInsideArchives = Boolean.parseBoolean(args[i + 1]);
                i++;
            } else if (ARG_SCOPE.equals(args[i]) && i + 1 < args.length) {
                scopeId = args[i + 1];
                i++;
            } else if (ARG_CUSTOM_PATH_BASE64.equals(args[i]) && i + 1 < args.length) {
                customPathText = decodeBase64(args[i + 1]);
                i++;
            }
        }

        if (autoStart) {
            searchRequest = new SearchRequest(
                    rawPatterns,
                    includeJarInJar,
                    caseInsensitive,
                    regex,
                    checkFileNames,
                    searchInsideArchives,
                    scopeId,
                    customPathText
            );
        }

        LOGGER.info("Standalone Mods Searcher mods folder: {}", ModListUtils.MODS_FOLDER.toAbsolutePath().normalize());
        LOGGER.info("Standalone Mods Searcher working directory: {}", WORKSPACE_ROOT);
        if (searchRequest != null) {
            LOGGER.info("Standalone Mods Searcher auto-start request: patterns={}, scope={}",
                    searchRequest.rawPatterns.replace("\n", "\\n"),
                    searchRequest.scopeId);
        }
        return new ChildLaunchArguments(searchRequest);
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

    private static String encodeBase64(String value) {
        String safeValue = value == null ? "" : value;
        return Base64.getEncoder().encodeToString(safeValue.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static class ChildLaunchArguments {
        private final SearchRequest searchRequest;

        private ChildLaunchArguments(SearchRequest searchRequest) {
            this.searchRequest = searchRequest;
        }
    }

    public static class SearchRequest {
        private final String rawPatterns;
        private final boolean includeJarInJar;
        private final boolean caseInsensitive;
        private final boolean regex;
        private final boolean checkFileNames;
        private final boolean searchInsideArchives;
        private final String scopeId;
        private final String customPathText;

        public SearchRequest(String rawPatterns,
                             boolean includeJarInJar,
                             boolean caseInsensitive,
                             boolean regex,
                             boolean checkFileNames,
                             boolean searchInsideArchives,
                             String scopeId,
                             String customPathText) {
            this.rawPatterns = rawPatterns == null ? "" : rawPatterns;
            this.includeJarInJar = includeJarInJar;
            this.caseInsensitive = caseInsensitive;
            this.regex = regex;
            this.checkFileNames = checkFileNames;
            this.searchInsideArchives = searchInsideArchives;
            this.scopeId = scopeId == null ? "root" : scopeId;
            this.customPathText = customPathText == null ? "" : customPathText;
        }

        public static SearchRequest modsStringMatch(String rawPatterns) {
            return new SearchRequest(rawPatterns, true, false, false, true, true, "mods", "");
        }
    }
}
