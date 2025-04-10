package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.loading_utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
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
        } catch (IOException ex) {
            return false;
        }
    }

    public static String getJDepsPath() {
        // Option 1: Derive from java binary location.
        String javaBinaryPath = JavaBinaryLocator.getJavaBinary(ProcessHandle.current());
        if (javaBinaryPath.contains("javaw")) {
            javaBinaryPath = javaBinaryPath.replace("javaw", "java");
        }
        String jdepsPath = javaBinaryPath.replaceAll("(?<=[/\\\\])java(\\.exe)?$", "jdeps$1");
        if (validateJdepsPath(jdepsPath)) return jdepsPath;

        // Option 2: Use JAVA_HOME environment variable.
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            String osName = System.getProperty("os.name").toLowerCase();
            // Check if javaHome already ends with 'bin'
            if (javaHome.endsWith("bin") || javaHome.endsWith("bin" + File.separator)) {
                if (osName.contains("win")) {
                    jdepsPath = javaHome + File.separator + "jdeps.exe";
                } else {
                    jdepsPath = javaHome + File.separator + "jdeps";
                }
                if (validateJdepsPath(jdepsPath)) {
                    return jdepsPath;
                }
            } else {
                // Otherwise, append bin directory.
                if (osName.contains("win")) {
                    jdepsPath = javaHome + File.separator + "bin" + File.separator + "jdeps.exe";
                } else {
                    jdepsPath = javaHome + File.separator + "bin" + File.separator + "jdeps";
                }
                if (validateJdepsPath(jdepsPath)) {
                    return jdepsPath;
                }
            }
        }

        // Option 3: Fall back to using just "jdeps" command.
        if (validateJdepsPath("jdeps")) {
            return "jdeps";
        }

        return null;
    }


    public static HashSet<String> getCreateClassesModUsing(Mod mod, String jdepsPath) {
        HashSet<String> result = new HashSet<>();
        try {
            ProcessBuilder jdepsProcessBuilder = new ProcessBuilder(
                    jdepsPath,
                    "-verbose:class",
                    Paths.get("mods", mod.getJarName()).toAbsolutePath().toString()
            );
            jdepsProcessBuilder.redirectErrorStream(true);
            Process process = jdepsProcessBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    int arrowIndex = line.indexOf("->");
                    if (arrowIndex != -1) {
                        String dependency = line.substring(arrowIndex + 2).trim();
                        if (dependency.endsWith(".class")) continue;
                        int spaceIndex = dependency.indexOf(' ');
                        String classPath = dependency.substring(0, spaceIndex == -1 ? dependency.length() : spaceIndex).replace('.', '/');
                        classPath += ".class";
                        if (isCreateClass(classPath)) {
                            result.add(fixClassName(classPath));
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing create mod deps: ", e);
        }
        return result;
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
        JDialog dialog = new JDialog(parent, LanguageProvider.get("gui.menu.analysis.create_dependencies") + " (" + LanguageProvider.get("gui.window_name") + ")", true);
        dialog.setLayout(new BorderLayout());

        // Top panel with status and progress
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel statusLabel = new JLabel("Analyzing mods...");
        JLabel currentJarLabel = new JLabel("Current mod: None");
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(currentJarLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);
        dialog.add(topPanel, BorderLayout.NORTH);

        // Text area for results with auto-scrolling disabled
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setCaretPosition(0);
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane scrollPane = new JScrollPane(textArea);
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
            // Check if JDK is available
            String jdepsPath = getJDepsPath();
            if (jdepsPath == null) {
                String message = "JDK is required for analysis of jar files. JRE is not suitable for this!\nWe've tried JAVA_HOME and java used for launching game.\n";
                SwingUtilities.invokeLater(() -> {
                    textArea.append(message);
                    CrashAssistantApp.LOGGER.info(message.trim());
                    addOkButton(dialog);
                });
                return;
            }

            // Check for multiple Create mods
            List<Mod> createMods = getCurrentCreateMods();
            if (createMods.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    String message = "No Create mod found.\n";
                    textArea.append(message);
                    CrashAssistantApp.LOGGER.info(message.trim());
                    addOkButton(dialog);
                });
                return;
            }
            if (createMods.size() > 1) {
                String message = "Multiple Create mods found: " +
                        createMods.stream().map(Mod::getJarName).collect(Collectors.joining(", ")) + "\n" +
                        "Analysis cannot proceed with multiple Create mods.\n";
                SwingUtilities.invokeLater(() -> {
                    textArea.append(message);
                    CrashAssistantApp.LOGGER.info(message.trim());
                    addOkButton(dialog);
                });
                return;
            }

            // Proceed with single Create mod analysis
            Mod createMod = createMods.get(0);
            Set<String> currentCreateClasses = getCurrentCreateClasses(createMod);

            List<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true).stream()
                    .filter(mod -> !Objects.equals(mod.getModId(), "create"))
                    .toList();
            int totalMods = modsToAnalyze.size();

            if (totalMods == 0) {
                SwingUtilities.invokeLater(() -> {
                    String message = "No mods to analyze.\n";
                    textArea.append(message);
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
                        String message = String.format(
                                "Found %d Create mod class dependency(ies) in %s, which are missing from the current %s\n",
                                invalidDeps.size(), mod.getJarName(), createMod.getJarName()
                        );
                        SwingUtilities.invokeLater(() -> {
                            if (!isCancelled) {
                                textArea.append(message);
                                CrashAssistantApp.LOGGER.info(message.trim());
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
                        String message = String.format(
                                "Haven't found in any mod, Create mod class dependency(ies), which are missing from the current %s\n",
                                createMod.getJarName()
                        );
                        textArea.append(message);
                        CrashAssistantApp.LOGGER.info(message.trim());
                    } else {
                        StringBuilder detailedMessage = new StringBuilder();
                        detailedMessage.append("\n\n\nDetailed walkthrough of mods which rely on missing Create mod classes:\n");
                        List<Mod> sortedMods = new ArrayList<>(missingClassesMap.keySet());
                        sortedMods.sort(Comparator.comparing(Mod::getJarName));

                        for (Mod mod : sortedMods) {
                            Set<String> missingClasses = missingClassesMap.get(mod);
                            List<String> sortedClasses = new ArrayList<>(missingClasses);
                            Collections.sort(sortedClasses);

                            detailedMessage.append(String.format(
                                    "Mod: %s\nMissing classes of create:\n%s\n\n",
                                    mod.getJarName(),
                                    String.join("\n", sortedClasses)
                            ));
                        }
                        String detailedText = detailedMessage.toString();
                        textArea.append(detailedText);
                        CrashAssistantApp.LOGGER.info(detailedText.trim());
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
}