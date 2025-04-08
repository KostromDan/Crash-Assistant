package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.loading_utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
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

public class Create6Addons extends KnownCrashReason {
    public Create6Addons() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.c6a", new HashMap<>() {{
                    put("$LINK.C6A$", LanguageProvider.get("warnings_common.here"));
                }}),
                "Error loading class: com/simibubi/create/.* \\(java\\.lang\\.ClassNotFoundException: com\\.simibubi\\.create.*\\)",
                "Error loading class: com/jozufozu/flywheel/.* \\(java\\.lang\\.ClassNotFoundException: com\\.jozufozu\\.flywheel.*\\)",
                "Caused by: org\\.spongepowered\\.asm\\.mixin\\.throwables\\.ClassMetadataNotFoundException: net\\.createmod\\.catnip\\.data\\.Couple"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) {
            return false;
        }
        List<Mod> createMods = getCurrentCreateMods();
        if (createMods.isEmpty()) {
            return false;
        }
        if (createMods.size() == 1 &&
                createMods.get(0).getVersion() != null &&
                createMods.get(0).getVersion().startsWith("6") &&
                ModListUtils.getCurrentModList(true).stream()
                        .anyMatch(mod -> Objects.equals(mod.getModId(), "railways"))) {
            return true;
        }
        return super.matches(log);
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

    public static String getJDepsPath() {
        String javaBinaryPath = JavaBinaryLocator.getJavaBinary(ProcessHandle.current());
        String jdepsPath = javaBinaryPath.replaceAll("(?<=[/\\\\])java(\\.exe)?$", "jdeps$1");
        if (Files.isRegularFile(Paths.get(javaBinaryPath)) && !Files.isRegularFile(Paths.get(jdepsPath))) {
            CrashAssistantApp.LOGGER.error("JDK is required for analysis of jar files. JRE is not suitable for this!");
            return null;
        }
        return jdepsPath;
    }

    public static HashSet<String> getCreateClassesModUsing(Mod mod) {
        HashSet<String> result = new HashSet<>();
        try {
            ProcessBuilder jdepsProcessBuilder = new ProcessBuilder(
                    getJDepsPath(),
                    "-verbose:class",
                    "mods/" + mod.getJarName()
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
        JDialog dialog = new JDialog(parent, "Create Dependencies Analysis", true);
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
        // Prevent auto-scrolling by disabling caret update policy
        textArea.setCaretPosition(0); // Start at the top
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE); // Disable auto-scrolling
        JScrollPane scrollPane = new JScrollPane(textArea);
        dialog.add(scrollPane, BorderLayout.CENTER);

        dialog.setSize(800, 500);
        dialog.setLocationRelativeTo(parent);

        // Start analysis in a background thread
        new Thread(() -> {
            // Check if JDK is available
            if (getJDepsPath() == null) {
                String message = "JDK is required for analysis of jar files. JRE is not suitable for this!\n";
                SwingUtilities.invokeLater(() -> {
                    textArea.append(message);
                    addOkButton(dialog);
                });
                CrashAssistantApp.LOGGER.error(message.trim());
                return;
            }

            // Check for multiple Create mods and interrupt if necessary
            List<Mod> createMods = getCurrentCreateMods();
            if (createMods.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    textArea.append("No Create mod found.\n");
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
                    addOkButton(dialog);
                });
                CrashAssistantApp.LOGGER.error(message.trim());
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
                    textArea.append("No mods to analyze.\n");
                    addOkButton(dialog);
                });
                return;
            }

            // Collect missing classes during analysis
            Map<Mod, Set<String>> missingClassesMap = new ConcurrentHashMap<>();
            AtomicInteger completedTasks = new AtomicInteger(0);
            SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            for (Mod mod : modsToAnalyze) {
                executor.submit(() -> {
                    SwingUtilities.invokeLater(() -> currentJarLabel.setText("Current mod: " + mod.getJarName()));
                    HashSet<String> deps = getCreateClassesModUsing(mod);
                    Set<String> invalidDeps = deps.stream()
                            .filter(dep -> !currentCreateClasses.contains(dep))
                            .collect(Collectors.toSet());

                    if (!invalidDeps.isEmpty()) {
                        missingClassesMap.put(mod, invalidDeps);
                        String message = String.format(
                                "Found %d Create mod class dependency(ies) in %s, which are missing from the current %s\n",
                                invalidDeps.size(), mod.getJarName(), createMod.getJarName()
                        );
                        SwingUtilities.invokeLater(() -> textArea.append(message));
                    }

                    int completed = completedTasks.incrementAndGet();
                    SwingUtilities.invokeLater(() -> progressBar.setValue(completed));
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Display results after analysis
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Analysis complete");
                currentJarLabel.setText("Current mod: None");

                if (missingClassesMap.isEmpty()) {
                    String message = "No issues found.\n";
                    textArea.append(message);
                    CrashAssistantApp.LOGGER.info(message.trim());
                } else {
                    StringBuilder detailedMessage = new StringBuilder();
                    detailedMessage.append("\n\n\nDetailed walkthrough of mods with missing classes:\n");
                    for (Map.Entry<Mod, Set<String>> entry : missingClassesMap.entrySet()) {
                        Mod mod = entry.getKey();
                        Set<String> missingClasses = entry.getValue();
                        detailedMessage.append(String.format(
                                "Mod: %s\nMissing classes:\n%s\n\n",
                                mod.getJarName(),
                                String.join("\n", missingClasses)
                        ));
                    }
                    textArea.append(detailedMessage.toString());
                    CrashAssistantApp.LOGGER.info(detailedMessage.toString());
                }
                addOkButton(dialog);
            });
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