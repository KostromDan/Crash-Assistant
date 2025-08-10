package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.ComparableVersion;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

public class CreateDependencies {
    private static volatile boolean isCancelled = false;
    private static final List<Process> runningProcesses = Collections.synchronizedList(new ArrayList<>());
    private static ExecutorService executor; // Declared as a static field

    // Style constants
    private static final Color ERROR_COLOR = Color.RED;
    private static final Color NORMAL_COLOR = Color.BLACK;
    private static final Color CREATE_MOD_COLOR = Color.BLUE;

    /**
     * Appends styled text to a JTextPane
     *
     * @param textPane the JTextPane to append text to
     * @param text     the text to append
     * @param color    the color to use for the text
     */
    private static void appendStyledText(JTextPane textPane, String text, Color color) {
        StyledDocument doc = textPane.getStyledDocument();
        Style style = textPane.addStyle("Color Style", null);
        StyleConstants.setForeground(style, color);

        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            CrashAssistantApp.LOGGER.error("Error appending styled text: ", e);
            // Fallback to normal append without styling
            try {
                doc.insertString(doc.getLength(), text, null);
            } catch (BadLocationException ignored) {
                // If even this fails, we can't do much
            }
        }
    }

    public static List<Mod> getCurrentCreateMods() {
        return ModListUtils.getCurrentModList(true).stream()
                .filter(mod -> Objects.equals(mod.getModId(), "create"))
                .collect(Collectors.toList());
    }

    public static boolean isCreateClass(String className) {
        return (className.startsWith("com/simibubi/create") ||
                className.startsWith("com/jozufozu/flywheel") ||
                className.startsWith("net/createmod") ||
                className.startsWith("dev/engine_room")) &&
                className.endsWith(".class");
    }

    public static String fixClassName(String className) {
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        int dollarIndex = className.indexOf('$');
        if (dollarIndex != -1) {
            className = className.substring(0, dollarIndex);
        }
        return className;
    }

    public static boolean validateJdepsPath(String jdepsPath) {
        try {
            new ProcessBuilder(jdepsPath, "-version").start();
            return true;
        } catch (Exception ex) {
            CrashAssistantApp.LOGGER.warn("Error while trying jdeps path: {}\n{}", jdepsPath, ex.getMessage());
            return false;
        }
    }

    public static String transformJavaHomeToJdepsPath(String javaHome) {
        if (javaHome != null && !javaHome.isEmpty()) {
            String osName = System.getProperty("os.name").toLowerCase();
            // Check if javaHome already ends with 'bin'
            if (javaHome.endsWith(File.separator)) {
                javaHome = javaHome.substring(0, javaHome.length() - 1);
            }
            if (javaHome.endsWith("bin")) {
                if (osName.contains("win")) {
                    return javaHome + File.separator + "jdeps.exe";
                } else {
                    return javaHome + File.separator + "jdeps";
                }
            } else {
                // Otherwise, append bin directory.
                if (osName.contains("win")) {
                    return javaHome + File.separator + "bin" + File.separator + "jdeps.exe";
                } else {
                    return javaHome + File.separator + "bin" + File.separator + "jdeps";
                }
            }
        }
        return null;
    }

    public static String transformJavaBinaryPathToJdepsPath(String javaBinaryPath) {
        return javaBinaryPath.replaceAll("(?<=[/\\\\])java(\\.exe)?$", "jdeps$1");
    }

    /**
     * Removes vendor prefixes like "corretto-", "azul-", "jdk-" from the folder name
     * before comparing versions.
     *
     * @param folderName The folder name to process
     * @return The folder name without vendor prefix
     */
    private static String removeVendorPrefix(String folderName) {
        // Remove vendor prefixes like "corretto-", "azul-", "jdk-"
        return folderName.replaceAll("^[a-zA-Z]+-", "");
    }

    public static String getJDepsPath() {
        // Option 1: Derive from java binary location.
        String javaBinaryPath = JavaBinaryLocator.getJavaBinary();
        if (javaBinaryPath.contains("javaw")) {
            javaBinaryPath = javaBinaryPath.replace("javaw", "java");
        }
        String jdepsPath = transformJavaBinaryPathToJdepsPath(javaBinaryPath);
        if (validateJdepsPath(jdepsPath)) return jdepsPath;

        // Option 2: Use JAVA_HOME environment variable.
        String javaHome = System.getenv("JAVA_HOME");
        CrashAssistantApp.LOGGER.info("System.getenv(\"JAVA_HOME\"): {}", javaHome);
        String javaHomeToJdepsPath = transformJavaHomeToJdepsPath(javaHome);
        if (validateJdepsPath(javaHomeToJdepsPath)) {
            return javaHomeToJdepsPath;
        }

        // Option 3: Try to get JAVA_HOME from echo command.
        try {
            ProcessBuilder processBuilder;
            if (PlatformHelp.isWindows()) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", "echo %JAVA_HOME%");
            } else {
                processBuilder = new ProcessBuilder("/bin/sh", "-c", "echo $JAVA_HOME");
            }
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String echoOutput = reader.readLine();
            CrashAssistantApp.LOGGER.info("Echo JAVA_HOME output: {}", echoOutput);

            String echoJdepsPath = transformJavaHomeToJdepsPath(echoOutput);
            if (validateJdepsPath(echoJdepsPath)) {
                return echoJdepsPath;
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Error while trying to get JAVA_HOME from echo: {}", e.getMessage());
        }

        // Option 4: Try to get Java from java command location.
        try {
            ProcessBuilder processBuilder;
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", "where java");
            } else {
                processBuilder = new ProcessBuilder("/bin/sh", "-c", "which java");
            }
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String javaPath = reader.readLine();
            CrashAssistantApp.LOGGER.info("Java command location: {}", javaPath);

            String cmdJdepsPath = transformJavaBinaryPathToJdepsPath(javaPath);
            if (validateJdepsPath(cmdJdepsPath)) {
                return cmdJdepsPath;
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Error while trying to get Java from command location: {}", e.getMessage());
        }

        // Option 5: Using just "jdeps" command.
        if (validateJdepsPath("jdeps")) {
            return "jdeps";
        }

        // Option 6: Try to find Java installations in common directories
        if (PlatformHelp.isWindows()) {
            List<String> javaDirectories = new ArrayList<>();
            javaDirectories.add("C:\\Program Files\\Java");
            javaDirectories.add("C:\\Program Files\\Eclipse Adoptium");
            javaDirectories.add(System.getProperty("user.home") + "\\.jdks");


            List<File> javaFolders = new ArrayList<>();
            for (String directory : javaDirectories) {
                File dir = new File(directory);
                if (dir.exists() && dir.isDirectory()) {
                    File[] folders = dir.listFiles(File::isDirectory);
                    if (folders != null) {
                        for (File folder : folders) {
                            javaFolders.add(folder);
                        }
                    }
                }
            }

            // Sort folders by version (newer versions first)
            javaFolders.sort((f1, f2) -> {
                try {
                    // Remove vendor prefixes like "corretto-", "azul-", "jdk-" before comparing
                    String name1 = removeVendorPrefix(f1.getName());
                    String name2 = removeVendorPrefix(f2.getName());

                    ComparableVersion v1 = new ComparableVersion(name1);
                    ComparableVersion v2 = new ComparableVersion(name2);
                    return v2.compareTo(v1); // Reverse order to get newer versions first
                } catch (Exception e) {
                    CrashAssistantApp.LOGGER.warn("Error while comparing Java versions: {}", e.getMessage());
                    return f1.getName().compareTo(f2.getName()); // Fallback to alphabetical order
                }
            });

            // Try each folder
            for (File folder : javaFolders) {
                String folderJdepsPath = transformJavaHomeToJdepsPath(folder.getAbsolutePath());
                if (validateJdepsPath(folderJdepsPath)) {
                    return folderJdepsPath;
                }
            }
        }

        // Option 7: Java path from local config.
        String valueFromConfig = (String) CrashAssistantLocalConfig.get("JDK_PATH");
        if (valueFromConfig != null && !valueFromConfig.isEmpty()) {
            String jdepsPathFromLocalConfig = transformJavaHomeToJdepsPath(valueFromConfig);
            if (validateJdepsPath(jdepsPathFromLocalConfig)) {
                return jdepsPathFromLocalConfig;
            }
        } else if (valueFromConfig != null) {
            CrashAssistantApp.LOGGER.warn("JDK_PATH is empty in local config.");
        }

        return null;
    }

    public static HashSet<String> getCurrentCreateClasses(Mod createMod) {
        HashSet<String> currentCreateClasses = new HashSet<>();
        try {
            try (JarFile jarFile = new JarFile(Paths.get("mods", createMod.getJarName()).toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (isCreateClass(name)) {
                        currentCreateClasses.add(fixClassName(name));
                        continue;
                    }
                    if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                        InputStream nestedJarStream = jarFile.getInputStream(entry);
                        try (JarInputStream nestedJar = new JarInputStream(nestedJarStream)) {
                            JarEntry nestedEntry;
                            while ((nestedEntry = nestedJar.getNextJarEntry()) != null) {
                                String className = nestedEntry.getName();
                                if (isCreateClass(className)) {
                                    currentCreateClasses.add(fixClassName(className));
                                }
                            }
                        }
                    }
                }
            }
            CrashAssistantApp.LOGGER.info("Found " + currentCreateClasses.size() + " create classes in " + createMod.getJarName());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing create mod deps: ", e);
        }
        return currentCreateClasses;
    }

    public static void showCreateAnalysisDialog(JFrame parent) {
        JDialog dialog = new JDialog(
                parent,
                LanguageProvider.get("gui.menu.analysis.create_dependencies")
                        + " (" + LanguageProvider.get("gui.window_name") + ")",
                true
        );
        dialog.setLayout(new BorderLayout());

        // Static text header, left‑aligned, with two lines via HTML
        JLabel headerLabel = new JLabel(
                "<html>"
                        + "Wait for analysis to finish.<br>"
                        + "Try removing/updating/downgrading all <font color='red'>problematic mods</font> detected below to match the current <font color='#0000FF'>Create mod</font> version.<br>" +
                        "&nbsp;"
                        + "</html>"
        );
        headerLabel.setHorizontalAlignment(SwingConstants.LEFT);

        // Top panel with status and progress, labels left‑aligned
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel statusLabel = new JLabel("Analyzing mods...");
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JLabel currentJarLabel = new JLabel("Current mod: None");
        currentJarLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(currentJarLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        // Wrap header and topPanel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerLabel, BorderLayout.NORTH);
        headerPanel.add(topPanel, BorderLayout.SOUTH);

        dialog.add(headerPanel, BorderLayout.NORTH);

        // Text pane for results with styled text
        JTextPane textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setCaretPosition(0);
        DefaultCaret caret = (DefaultCaret) textPane.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane scrollPane = new JScrollPane(textPane);
        dialog.add(scrollPane, BorderLayout.CENTER);

        dialog.setSize(900, 500);
        dialog.setLocationRelativeTo(parent);

        // Reset state for new analysis
        isCancelled = false;
        runningProcesses.clear();

        // Initialize executor for this analysis
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        // Add window listener to handle dialog closing
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CrashAssistantApp.LOGGER.info("Window closed, interrupting all create dep analysis processes...");
                isCancelled = true;
                executor.shutdownNow();
                synchronized (runningProcesses) {
                    for (Process p : runningProcesses) {
                        p.destroy();
                    }
                    runningProcesses.clear();
                }
            }
        });

        // Start analysis in a background thread
        new Thread(() -> {
            // Check for multiple Create mods
            List<Mod> createMods = getCurrentCreateMods();
            if (createMods.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    String message = "No Create mod found.\n";
                    appendStyledText(textPane, message, NORMAL_COLOR);
                    CrashAssistantApp.LOGGER.info(message.trim());
                    addOkButton(dialog);
                });
                return;
            }
            if (createMods.size() > 1) {
                String message = "Multiple Create mods found: " +
                        createMods.stream().map(Mod::getJarName).collect(Collectors.joining(", ")) + "\n" +
                        "Analysis cannot proceed with multiple Create mods.\n" +
                        "Please fix the duplicated mods issue first.\n";
                SwingUtilities.invokeLater(() -> {
                    appendStyledText(textPane, message, ERROR_COLOR);
                    CrashAssistantApp.LOGGER.info(message.trim());
                    addOkButton(dialog);
                });
                return;
            }

            // Check if JDK is available
            String jdepsPath = getJDepsPath();
            if (jdepsPath == null) {
                SwingUtilities.invokeLater(() -> {
                    // Show JDK warning dialog
                    showJdepsWarn(parent, dialog);
                    dialog.dispose();
                });
                return;
            }

            // Proceed with single Create mod analysis
            Mod createMod = createMods.get(0);
            Set<String> currentCreateClasses = getCurrentCreateClasses(createMod);

            List<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true).stream()
                    .filter(mod -> !Objects.equals(mod.getModId(), "create"))
                    .collect(Collectors.toList());
            int totalMods = modsToAnalyze.size();

            if (totalMods == 0) {
                SwingUtilities.invokeLater(() -> {
                    String message = "No mods to analyze.\n";
                    appendStyledText(textPane, message, NORMAL_COLOR);
                    CrashAssistantApp.LOGGER.info(message.trim());
                    addOkButton(dialog);
                });
                return;
            }

            // Collect missing classes during analysis
            Map<Mod, Set<String>> missingClassesMap = new ConcurrentHashMap<>();
            AtomicInteger completedTasks = new AtomicInteger(0);
            SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

            for (Mod mod : modsToAnalyze) {
                executor.submit(() -> {
                    if (isCancelled) return;

                    SwingUtilities.invokeLater(() -> currentJarLabel.setText("Current mod: " + mod.getJarName()));
                    Process process = null;
                    HashSet<String> deps = new HashSet<>();
                    try {
                        ProcessBuilder jdepsProcessBuilder = new ProcessBuilder(
                                jdepsPath,
                                "-verbose:class",
                                Paths.get("mods", mod.getJarName()).toAbsolutePath().toString()
                        );
                        jdepsProcessBuilder.redirectErrorStream(true);
                        process = jdepsProcessBuilder.start();
                        runningProcesses.add(process);

                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (isCancelled) break;
                                line = line.trim();
                                int arrowIndex = line.indexOf("->");
                                if (arrowIndex != -1) {
                                    String dependency = line.substring(arrowIndex + 2).trim();
                                    if (dependency.endsWith(".class")) continue;
                                    int spaceIndex = dependency.indexOf(' ');
                                    String classPath = dependency.substring(0, spaceIndex == -1 ? dependency.length() : spaceIndex).replace('.', '/');
                                    classPath += ".class";
                                    if (isCreateClass(classPath)) {
                                        deps.add(fixClassName(classPath));
                                    }
                                }
                            }
                        }
                        process.waitFor();
                    } catch (InterruptedException ignored) {
                        CrashAssistantApp.LOGGER.warn("Analysis of " + mod.getJarName() + " was interrupted.");
                    } catch (Exception e) {
                        CrashAssistantApp.LOGGER.error("Error while analysing create mod deps for " + mod.getJarName() + ": ", e);
                    } finally {
                        if (process != null) {
                            runningProcesses.remove(process);
                        }
                    }

                    Set<String> invalidDeps = deps.stream()
                            .filter(dep -> !currentCreateClasses.contains(dep))
                            .collect(Collectors.toSet());

                    if (!invalidDeps.isEmpty()) {
                        missingClassesMap.put(mod, invalidDeps);
                        final String jarName = mod.getJarName();
                        final int depCount = invalidDeps.size();
                        final String createJarName = createMod.getJarName();

                        SwingUtilities.invokeLater(() -> {
                            if (!isCancelled) {
                                // Mark class count and problematic mod name in red, Create mod name in blue
                                appendStyledText(textPane, "Found ", NORMAL_COLOR);
                                appendStyledText(textPane, String.valueOf(depCount), ERROR_COLOR);
                                appendStyledText(textPane, " Create mod class dependency(ies) in ", NORMAL_COLOR);
                                appendStyledText(textPane, jarName, ERROR_COLOR);
                                appendStyledText(textPane, ", which are missing from the current ", NORMAL_COLOR);
                                appendStyledText(textPane, createJarName, CREATE_MOD_COLOR);
                                appendStyledText(textPane, "\n", NORMAL_COLOR);

                                // Log the complete message
                                String logMessage = String.format(
                                        "Found %d Create mod class dependency(ies) in %s, which are missing from the current %s",
                                        depCount, jarName, createJarName
                                );
                                CrashAssistantApp.LOGGER.info(logMessage);
                            }
                        });
                    }

                    int completed = completedTasks.incrementAndGet();
                    SwingUtilities.invokeLater(() -> {
                        if (!isCancelled) {
                            progressBar.setValue(completed);
                        }
                    });
                });
            }

            // Shutdown executor and wait for tasks to complete
            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Display results after analysis only if not cancelled
            if (!isCancelled) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Analysis complete");
                    currentJarLabel.setText("Current mod: None");

                    if (missingClassesMap.isEmpty()) {
                        String createJarName = createMod.getJarName();
                        appendStyledText(textPane, "Haven't found in any mod, Create mod class dependency(ies), which are missing from the current ", NORMAL_COLOR);
                        appendStyledText(textPane, createJarName, CREATE_MOD_COLOR);
                        appendStyledText(textPane, "\n", NORMAL_COLOR);

                        // Log the complete message
                        String logMessage = String.format(
                                "Haven't found in any mod, Create mod class dependency(ies), which are missing from the current %s",
                                createJarName
                        );
                        CrashAssistantApp.LOGGER.info(logMessage);
                    } else {
                        // Header for detailed results
                        appendStyledText(textPane, "\n\n\nDetailed walkthrough of mods which rely on missing Create mod classes:\n", NORMAL_COLOR);

                        List<Mod> sortedMods = new ArrayList<>(missingClassesMap.keySet());
                        sortedMods.sort(Comparator.comparing(Mod::getJarName));

                        for (Mod mod : sortedMods) {
                            Set<String> missingClasses = missingClassesMap.get(mod);
                            List<String> sortedClasses = new ArrayList<>(missingClasses);
                            Collections.sort(sortedClasses);

                            // Only mod name in red, surrounding text in normal color
                            appendStyledText(textPane, "Mod: ", NORMAL_COLOR);
                            appendStyledText(textPane, mod.getJarName(), ERROR_COLOR);
                            appendStyledText(textPane, "\n", NORMAL_COLOR);

                            // Missing classes in normal color
                            appendStyledText(textPane, "Missing classes of create:\n", NORMAL_COLOR);
                            appendStyledText(textPane, String.join("\n", sortedClasses) + "\n\n", NORMAL_COLOR);

                            // Log the complete text for this mod
                            String logMessage = String.format(
                                    "Mod: %s\nMissing classes of create:\n%s\n\n",
                                    mod.getJarName(),
                                    String.join("\n", sortedClasses)
                            );
                            CrashAssistantApp.LOGGER.info(logMessage.trim());
                        }
                    }
                    addOkButton(dialog);
                });
            }
        }).start();

        dialog.setVisible(true);
    }

    private static void addOkButton(JDialog dialog) {
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        dialog.add(okButton, BorderLayout.SOUTH);
        dialog.revalidate();
    }

    /**
     * Shows a warning dialog when JDK is not found
     *
     * @param parent the parent frame
     * @param dialog the dialog to close after handling JDK installation
     * @return true if JDK was successfully configured, false otherwise
     */
    private static boolean showJdepsWarn(JFrame parent, JDialog dialog) {
        JDialog warnDialog = new JdkWarningDialog(parent, dialog);
        warnDialog.setVisible(true);

        // After dialog is closed, check if JDK path is now available
        String jdepsPath = getJDepsPath();
        return jdepsPath != null;
    }

    /**
     * Dialog for JDK warning with options to install or select JDK
     */
    private static class JdkWarningDialog extends JDialog {
        public JdkWarningDialog(JFrame parent, JDialog parentDialog) {
            super(parent, "JDK Required", true);
            setLayout(new BorderLayout());

            // Create text pane for the warning message
            String message = "<strong>JDK is required</strong> for analysis of jar files. <strong>JRE is not suitable</strong> for this!\n\n" +
                    "We've tried JAVA_HOME, jdeps cmd and java used for launching game.\n\n" +
                    "You have the following options:\n" +
                    "1. Install JDK via winget (<strong>Oracle JDK 21</strong>)\n" +
                    "2. Select an existing JDK installation directory\n" +
                    "3. Close this dialog and install JDK manually from:\n" +
                    "<a href=\"" + LinksProvider.ADOPTIUM_JDK.getLink() + "\">" + LinksProvider.ADOPTIUM_JDK.getLink() + "</a>\n" +
                    "Make sure to select <strong>JAVA_HOME</strong> check box in the installation settings.\n\n\n" +
                    "After any of this done, try to use this analysis again.";

            JEditorPane textPane = CrashAssistantGUI.getEditorPane(message, true, 550);
            JScrollPane scrollPane = new JScrollPane(textPane);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scrollPane, BorderLayout.CENTER);

            // Create button panel with three buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

            // Install JDK via winget button
            JButton installButton = new JButton("Install JDK via winget");
            installButton.addActionListener(e -> {
                try {
                    // Show info message
                    int option = JOptionPane.showConfirmDialog(
                            this,
                            "Installing JDK using winget. A console window will open to show progress.\n" +
                                    "Installation will start after clicking OK.\n\n" +
                                    "After finished, try analysis again.",
                            "Installing JDK",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    if (option != JOptionPane.OK_OPTION) {
                        return; // Don't proceed with installation if user cancels
                    }

                    // Run winget command to install JDK
                    ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", "winget", "install", "--id=Oracle.JDK.21", "-e");
                    pb.start();

                    // Close this dialog
                    dispose();

                    // Close parent dialog
                    if (parentDialog != null) {
                        parentDialog.dispose();
                    }
                } catch (Exception ex) {
                    CrashAssistantApp.LOGGER.error("Error installing JDK via winget: ", ex);
                    JOptionPane.showMessageDialog(
                            this,
                            "Error installing JDK: " + ex.getMessage(),
                            "Installation Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
            buttonPanel.add(installButton);

            // Select JDK button
            JButton selectButton = new JButton("Select JDK");
            selectButton.addActionListener(e -> {
                // Show info message
                JOptionPane.showMessageDialog(
                        this,
                        "Please specify path to JDK directory.",
                        "Select JDK",
                        JOptionPane.INFORMATION_MESSAGE
                );

                // Open folder selection dialog
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fileChooser.setDialogTitle("Select JDK Directory");
                fileChooser.setCurrentDirectory(new File("C:\\"));
                fileChooser.setPreferredSize(new Dimension(600, 400));

                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File selectedFolder = fileChooser.getSelectedFile();
                    String jdkPath = selectedFolder.getAbsolutePath();

                    // Save to local config
                    CrashAssistantLocalConfig.set("JDK_PATH", jdkPath);

                    // Show success message
                    JOptionPane.showMessageDialog(
                            this,
                            "Success! Try analysis again.",
                            "JDK Path Saved",
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    // Close this dialog
                    dispose();

                    // Close parent dialog
                    if (parentDialog != null) {
                        parentDialog.dispose();
                    }
                }
            });
            buttonPanel.add(selectButton);

            // Close button
            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> {
                dispose();

                // Close parent dialog
                if (parentDialog != null) {
                    parentDialog.dispose();
                }
            });
            buttonPanel.add(closeButton);

            add(buttonPanel, BorderLayout.SOUTH);

            // Set dialog properties
            setSize(600, 400);
            setLocationRelativeTo(parent);
        }
    }
}
