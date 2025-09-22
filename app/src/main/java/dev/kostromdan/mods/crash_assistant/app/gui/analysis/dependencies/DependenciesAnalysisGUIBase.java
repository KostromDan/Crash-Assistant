package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.AnalysisGUIBase;
import dev.kostromdan.mods.crash_assistant.app.utils.JarEntriesScanner;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.ComparableVersion;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public abstract class DependenciesAnalysisGUIBase extends AnalysisGUIBase { 

    protected static class TargetInfo {
        public final Path path;
        public final String display;
        public TargetInfo(Path path, String display) {
            this.path = path;
            this.display = display;
        }
    }

    protected static class JdepsScanResult {
        public boolean matched = false;
        public String matchedDisplay = null;
        public final HashSet<String> deps = new HashSet<>();
        public final Map<String, Set<String>> depsByDisplay = new HashMap<>();
    }

    protected abstract void recreateSelf();

    private JCheckBox includeNestedCheckbox;
    private volatile boolean isRestarting = false;

    public DependenciesAnalysisGUIBase(JFrame parent, String title, String headerText) {
        super(parent, title, headerText);
        initOptions();
    }

    private void initOptions() {
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        boolean defaultValue = true;
        Object saved = CrashAssistantLocalConfig.get("analysis.jdeps.include_nested");
        boolean current = saved instanceof Boolean ? (Boolean) saved : defaultValue;
        if (!(saved instanceof Boolean)) {
            CrashAssistantLocalConfig.set("analysis.jdeps.include_nested", defaultValue);
        }
        includeNestedCheckbox = new JCheckBox(LanguageProvider.get("gui.analysis.jdeps.include_nested"), current);
        includeNestedCheckbox.addActionListener(e -> onIncludeNestedChanged());
        optionsPanel.add(includeNestedCheckbox);
        addToHeaderCenter(optionsPanel);
    }

    private void onIncludeNestedChanged() {
        if (isRestarting) return;
        boolean newValue = includeNestedCheckbox.isSelected();
        int res = JOptionPane.showConfirmDialog(
                dialog,
                LanguageProvider.get("gui.analysis.jdeps.restart_prompt"),
                LanguageProvider.get("gui.analysis.jdeps.restart_title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (res == JOptionPane.YES_OPTION) {
            CrashAssistantLocalConfig.set("analysis.jdeps.include_nested", newValue);
            // Fully cancel and dispose this analysis GUI, then recreate a new one
            fullyRestartGui();
        } else {
            isRestarting = true;
            includeNestedCheckbox.setSelected(!newValue);
            isRestarting = false;
        }
    }

    protected boolean isIncludeNestedEnabled() {
        Object saved = CrashAssistantLocalConfig.get("analysis.jdeps.include_nested");
        return !(saved instanceof Boolean) || (Boolean) saved;
    }

    protected abstract Predicate<String> isRelevantClass();

    protected abstract String getModId();

    protected abstract String getModName();

    private void fullyRestartGui() {
        try {
            // Cancel running tasks and destroy processes
            isCancelled = true;
            if (executor != null) executor.shutdownNow();
            synchronized (runningProcesses) {
                for (Process p : runningProcesses) {
                    try { p.destroy(); } catch (Exception ignored) {}
                }
                runningProcesses.clear();
            }
        } catch (Exception ignored) {}
        // Dispose current dialog and recreate
        SwingUtilities.invokeLater(() -> {
            try {
                if (dialog != null) dialog.dispose();
            } catch (Exception ignored) {}
            recreateSelf();
        });
    }

    @Override
    protected void performAnalysis() {
        List<Mod> targetMods = ModListUtils.getCurrentModList(true).stream()
                .filter(mod -> Objects.equals(mod.getModId(), getModId()))
                .collect(Collectors.toList());

        if (targetMods.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                String msg = LanguageProvider.get("gui.analysis.dependencies.no_mod")
                        .replace("$MOD$", getModName());
                appendStyledText(msg, NORMAL_COLOR);
            });
            return;
        }

        if (targetMods.size() > 1) {
            String list = targetMods.stream().map(Mod::getJarName).collect(Collectors.joining(", "));
            String message = LanguageProvider.get("gui.analysis.dependencies.multiple_mods")
                    .replace("$MOD$", getModName())
                    .replace("$LIST$", list);
            SwingUtilities.invokeLater(() -> appendStyledText(message, ERROR_COLOR));
            return;
        }

        String jdepsPath = getJDepsPath();
        if (jdepsPath == null) {
            SwingUtilities.invokeLater(() -> {
                showJdepsWarn((JFrame) dialog.getParent(), dialog);
                dialog.dispose();
            });
            return;
        }
        CrashAssistantApp.LOGGER.info("Using jdeps at: \"{}\"", jdepsPath);

        // Cleanup temp directory for nested jars if needed
        if (isIncludeNestedEnabled()) {
            try { cleanJdepsTmp(); } catch (Exception ignored) {}
        }

        Mod targetMod = targetMods.get(0);
        Set<String> currentTargetClasses = getCurrentTargetClasses(targetMod);

        List<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true).stream()
                .filter(mod -> !Objects.equals(mod.getModId(), getModId()))
                .collect(Collectors.toList());
        int totalMods = modsToAnalyze.size();

        if (totalMods == 0) {
            SwingUtilities.invokeLater(() -> {
                appendStyledText(LanguageProvider.get("gui.analysis.dependencies.no_other_mods"), NORMAL_COLOR);
            });
            return;
        }

        Map<Mod, Set<String>> missingClassesMap = new ConcurrentHashMap<>();
        Map<Mod, String> modDisplayMap = new ConcurrentHashMap<>();
        AtomicInteger completedTasks = new AtomicInteger(0);
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;

                SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + mod.getJarName()));

                JdepsScanResult scan = scanModWithJdeps(mod, jdepsPath, isRelevantClass(), false);

                Set<String> invalidDeps = scan.deps.stream()
                        .filter(dep -> !currentTargetClasses.contains(dep))
                        .collect(Collectors.toSet());
                Map<String, Set<String>> depsByDisplay = scan.depsByDisplay;

                if (!invalidDeps.isEmpty()) {
                    missingClassesMap.put(mod, invalidDeps);
                    // determine a display for where the deps were found (prefer nested target display containing invalid deps)
                    String displayForMod = mod.getJarName();
                    outerCheck:
                    for (Map.Entry<String, Set<String>> e : depsByDisplay.entrySet()) {
                        for (String c : e.getValue()) {
                            if (!currentTargetClasses.contains(c)) {
                                displayForMod = e.getKey();
                                break outerCheck;
                            }
                        }
                    }
                    final String jarDisplay = displayForMod;
                    final int depCount = invalidDeps.size();
                    final String targetJarName = targetMod.getJarName();
                    modDisplayMap.put(mod, jarDisplay);

                    SwingUtilities.invokeLater(() -> {
                        if (!isCancelled) {
                            appendStyledText(LanguageProvider.get("gui.analysis.dependencies.found") + " ", NORMAL_COLOR);
                            appendStyledText(String.valueOf(depCount), ERROR_COLOR);
                            appendStyledText(
                                    LanguageProvider.get("gui.analysis.dependencies.dependencies_in")
                                            .replace("$MOD$", getModName()),
                                    NORMAL_COLOR);
                            appendStyledText(jarDisplay, ERROR_COLOR);
                            appendStyledText(LanguageProvider.get("gui.analysis.dependencies.missing_from_current"), NORMAL_COLOR);
                            appendStyledText(targetJarName, MOD_COLOR);
                            appendStyledText("\n", NORMAL_COLOR);

                            String logMessage = String.format(
                                    "Found %d " + getModName() + " mod class dependency(ies) in %s, which are missing from the current %s",
                                    depCount, jarDisplay, targetJarName
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

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!isCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (missingClassesMap.isEmpty()) {
                    String targetJarName = targetMod.getJarName();
                    appendStyledText(
                            LanguageProvider.get("gui.analysis.dependencies.none_missing_start")
                                    .replace("$MOD$", getModName()),
                            NORMAL_COLOR);
                    appendStyledText(targetJarName, MOD_COLOR);
                    appendStyledText("\n", NORMAL_COLOR);

                    String logMessage = String.format(
                            "Haven't found in any mod, " + getModName() + " mod class dependency(ies), which are missing from the current %s",
                            targetJarName
                    );
                    CrashAssistantApp.LOGGER.info(logMessage);
                } else {
                    appendStyledText(
                            LanguageProvider.get("gui.analysis.dependencies.walkthrough")
                                    .replace("$MOD$", getModName()),
                            NORMAL_COLOR);

                    List<Mod> sortedMods = new ArrayList<>(missingClassesMap.keySet());
                    sortedMods.sort(Comparator.comparing(Mod::getJarName));

                    for (Mod mod : sortedMods) {
                        Set<String> missingClasses = missingClassesMap.get(mod);
                        List<String> sortedClasses = new ArrayList<>(missingClasses);
                        Collections.sort(sortedClasses);

                        String displayName = modDisplayMap.get(mod) != null ? modDisplayMap.get(mod) : mod.getJarName();
                        appendStyledText(LanguageProvider.get("gui.analysis.dependencies.mod_label"), NORMAL_COLOR);
                        appendStyledText(displayName, ERROR_COLOR);
                        appendStyledText("\n", NORMAL_COLOR);

                        appendStyledText(
                                LanguageProvider.get("gui.analysis.dependencies.missing_classes")
                                        .replace("$MOD$", getModName()),
                                NORMAL_COLOR);
                        appendStyledText(String.join("\n", sortedClasses) + "\n\n", NORMAL_COLOR);

                        String logMessage = String.format(
                                "Mod: %s\nMissing classes of " + getModName() + ":\n%s\n\n",
                                displayName,
                                String.join("\n", sortedClasses)
                        );
                        CrashAssistantApp.LOGGER.info(logMessage.trim());
                    }
                }
            });
        }
    }

    private HashSet<String> getCurrentTargetClasses(Mod targetMod) {
        HashSet<String> currentTargetClasses = new HashSet<>();
        try {
            Path jarPath = Paths.get("mods", targetMod.getJarName());
            JarEntriesScanner.scanJar(jarPath, true, (containerName, entries) -> {
                for (Map.Entry<String, Boolean> e : entries.entrySet()) {
                    String name = e.getKey();
                    if (isRelevantClass().test(name)) {
                        currentTargetClasses.add(fixClassName(name));
                    }
                }
            });
            CrashAssistantApp.LOGGER.info("Found " + currentTargetClasses.size() + " " + getModName() + " classes in " + targetMod.getJarName());
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing " + getModName() + " mod deps: ", e);
        }
        return currentTargetClasses;
    }

    private String fixClassName(String className) {
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        int dollarIndex = className.indexOf('$');
        if (dollarIndex != -1) {
            className = className.substring(0, dollarIndex);
        }
        return className;
    }

    protected String getJDepsPath() {
        String javaBinaryPath = JavaBinaryLocator.getJavaBinary();
        if (javaBinaryPath.contains("javaw")) {
            javaBinaryPath = javaBinaryPath.replace("javaw", "java");
        }
        String jdepsPath = transformJavaBinaryPathToJdepsPath(javaBinaryPath);
        if (validateJdepsPath(jdepsPath)) return jdepsPath;

        String javaHome = System.getenv("JAVA_HOME");
        String javaHomeToJdepsPath = transformJavaHomeToJdepsPath(javaHome);
        if (validateJdepsPath(javaHomeToJdepsPath)) {
            return javaHomeToJdepsPath;
        }

        try {
            ProcessBuilder processBuilder = PlatformHelp.isWindows() ?
                    new ProcessBuilder("cmd.exe", "/c", "echo %JAVA_HOME%") :
                    new ProcessBuilder("/bin/sh", "-c", "echo $JAVA_HOME");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String echoOutput = reader.readLine();
            String echoJdepsPath = transformJavaHomeToJdepsPath(echoOutput);
            if (validateJdepsPath(echoJdepsPath)) {
                return echoJdepsPath;
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Error while trying to get JAVA_HOME from echo: {}", e.getMessage());
        }

        try {
            ProcessBuilder processBuilder = PlatformHelp.isWindows() ?
                    new ProcessBuilder("cmd.exe", "/c", "where java") :
                    new ProcessBuilder("/bin/sh", "-c", "which java");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String javaPath = reader.readLine();
            String cmdJdepsPath = transformJavaBinaryPathToJdepsPath(javaPath);
            if (validateJdepsPath(cmdJdepsPath)) {
                return cmdJdepsPath;
            }
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.warn("Error while trying to get Java from command location: {}", e.getMessage());
        }

        if (validateJdepsPath("jdeps")) {
            return "jdeps";
        }

        if (PlatformHelp.isWindows()) {
            List<String> javaDirectories = Arrays.asList("C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium", System.getProperty("user.home") + "\\.jdks");
            List<File> javaFolders = new ArrayList<>();
            for (String directory : javaDirectories) {
                File dir = new File(directory);
                if (dir.exists() && dir.isDirectory()) {
                    File[] folders = dir.listFiles(File::isDirectory);
                    if (folders != null) {
                        javaFolders.addAll(Arrays.asList(folders));
                    }
                }
            }

            javaFolders.sort((f1, f2) -> {
                try {
                    String name1 = removeVendorPrefix(f1.getName());
                    String name2 = removeVendorPrefix(f2.getName());
                    return new ComparableVersion(name2).compareTo(new ComparableVersion(name1));
                } catch (Exception e) {
                    return f1.getName().compareTo(f2.getName());
                }
            });

            for (File folder : javaFolders) {
                String folderJdepsPath = transformJavaHomeToJdepsPath(folder.getAbsolutePath());
                if (validateJdepsPath(folderJdepsPath)) {
                    return folderJdepsPath;
                }
            }
        }

        String valueFromConfig = (String) CrashAssistantLocalConfig.get("JDK_PATH");
        if (valueFromConfig != null && !valueFromConfig.isEmpty()) {
            String jdepsPathFromLocalConfig = transformJavaHomeToJdepsPath(valueFromConfig);
            if (validateJdepsPath(jdepsPathFromLocalConfig)) {
                return jdepsPathFromLocalConfig;
            }
        }

        return null;
    }

    protected boolean validateJdepsPath(String jdepsPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(jdepsPath, "-version");
            Process process = processBuilder.start();

            // Read the version output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String versionOutput = reader.readLine();

                if (versionOutput != null && !PlatformHelp.isJdkVersionSufficient(versionOutput)) {

                    CrashAssistantApp.LOGGER.warn("Found jdeps at \"{}\" but its version ({}) is lower than current major version ({})", 
                            jdepsPath, versionOutput.trim(), PlatformHelp.getCurrentJdkMajorVersion());
                    return false;
                }
            }

            process.waitFor();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    protected String transformJavaHomeToJdepsPath(String javaHome) {
        if (javaHome != null && !javaHome.isEmpty()) {
            String osName = System.getProperty("os.name").toLowerCase();
            if (javaHome.endsWith(File.separator)) {
                javaHome = javaHome.substring(0, javaHome.length() - 1);
            }
            if (javaHome.endsWith("bin")) {
                return javaHome + File.separator + (osName.contains("win") ? "jdeps.exe" : "jdeps");
            } else {
                return javaHome + File.separator + "bin" + File.separator + (osName.contains("win") ? "jdeps.exe" : "jdeps");
            }
        }
        return null;
    }

    protected String transformJavaBinaryPathToJdepsPath(String javaBinaryPath) {
        return javaBinaryPath.replaceAll("(?<=[/\\\\])java(\\.exe)?$", "jdeps$1");
    }

    protected String removeVendorPrefix(String folderName) {
        return folderName.replaceAll("^[a-zA-Z]+-", "");
    }

    protected void showJdepsWarn(JFrame parent, JDialog dialog) {
        new JdkWarningDialog(parent, dialog).setVisible(true);
    }

    private void cleanJdepsTmp() throws Exception {
        Path dir = Paths.get("local", "crash_assistant", "jdeps_tmp");
        if (!java.nio.file.Files.exists(dir)) return;
        try (java.util.stream.Stream<Path> walk = java.nio.file.Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }

    protected JdepsScanResult scanModWithJdeps(Mod mod, String jdepsPath, Predicate<String> predicate, boolean stopAfterFirst) {
        JdepsScanResult result = new JdepsScanResult();
        Process process = null;
        try {
            Path mainJarPath = Paths.get("mods", mod.getJarName()).toAbsolutePath();
            java.util.List<TargetInfo> targets = buildTargetInfosFromMod(mainJarPath, mod);
            outer:
            for (TargetInfo target : targets) {
                if (isCancelled) break;
                ProcessBuilder pb = new ProcessBuilder(jdepsPath, "-verbose:class", target.path.toString());
                pb.redirectErrorStream(true);
                process = pb.start();
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
                            String classPath = dependency.substring(0, spaceIndex == -1 ? dependency.length() : spaceIndex).replace('.', '/') + ".class";
                            if (predicate.test(classPath)) {
                                if (stopAfterFirst) {
                                    result.matched = true;
                                    result.matchedDisplay = target.display;
                                    break outer;
                                } else {
                                    String fixed = fixClassName(classPath);
                                    result.deps.add(fixed);
                                    result.depsByDisplay.computeIfAbsent(target.display, k -> new HashSet<>()).add(fixed);
                                }
                            }
                        }
                    }
                }
                process.waitFor();
                runningProcesses.remove(process);
            }
        } catch (InterruptedException ignored) {
            CrashAssistantApp.LOGGER.warn("Analysis of " + mod.getJarName() + " was interrupted.");
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error while analysing jdeps for " + mod.getJarName() + ": ", e);
        } finally {
            if (process != null) runningProcesses.remove(process);
        }
        return result;
    }

    protected List<TargetInfo> buildTargetInfosFromMod(Path mainJarPath, Mod mod) {
        List<TargetInfo> targets = new ArrayList<>();
        targets.add(new TargetInfo(mainJarPath.toAbsolutePath(), mod.getJarName()));
        if (!isIncludeNestedEnabled()) return targets;
        try {
            cleanJdepsTmp();
        } catch (Exception ignored) {}
        try {
            Path baseOut = Paths.get("local", "crash_assistant", "jdeps_tmp", mainJarPath.getFileName().toString());
            Files.createDirectories(baseOut);
            extractRecursivelyDetailed(mainJarPath, mod, baseOut, mod.getJarName(), targets);
        } catch (Exception ignored) {}
        return targets;
    }

    private void extractRecursivelyDetailed(Path currentJarPath, Mod currentMod, Path baseOut, String displayPrefix, List<TargetInfo> out) {
        List<Mod> children = currentMod.getJarJarMods();
        if (children == null || children.isEmpty()) return;
        try (JarFile jarFile = new JarFile(currentJarPath.toFile())) {
            for (Mod child : children) {
                String p = child.getPathFromJarJar();
                if (p == null) p = "";
                if (p.startsWith("/")) p = p.substring(1);
                String internal = p + child.getJarName();
                JarEntry entry = jarFile.getJarEntry(internal);
                if (entry == null || entry.isDirectory()) continue;
                Path outPath = baseOut.resolve(internal.replace('/', File.separatorChar));
                try {
                    Files.createDirectories(outPath.getParent());
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        Files.copy(in, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        Path childJar = outPath.toAbsolutePath();
                        String display = displayPrefix + "!/" + internal;
                        out.add(new TargetInfo(childJar, display));
                        extractRecursivelyDetailed(childJar, child, outPath.getParent(), display, out);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static class JdkWarningDialog extends JDialog {
        public JdkWarningDialog(JFrame parent, JDialog parentDialog) {
            super(parent, LanguageProvider.get("gui.analysis.dependencies.jdk_required_title"), true);
            setLayout(new BorderLayout());

            String message = LanguageProvider.get("gui.analysis.dependencies.jdk_required_message")
                    .replace("$ADOPTIUM_JDK_LINK$", LinksProvider.ADOPTIUM_JDK.getLink());

            JEditorPane textPane = CrashAssistantGUI.getEditorPane(message, true, 550);
            JScrollPane scrollPane = new JScrollPane(textPane);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scrollPane, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

            JButton installButton = new JButton(LanguageProvider.get("gui.analysis.dependencies.install_jdk_via_winget_button"));
            installButton.addActionListener(e -> {
                try {
                    int option = JOptionPane.showConfirmDialog(
                            this,
                            LanguageProvider.get("gui.analysis.dependencies.install_jdk_confirm_message"),
                            LanguageProvider.get("gui.analysis.dependencies.install_jdk_title"),
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    if (option != JOptionPane.OK_OPTION) {
                        return;
                    }

                    new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", "winget", "install", "--id=Oracle.JDK.21", "-e").start();
                    dispose();
                    if (parentDialog != null) {
                        parentDialog.dispose();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            LanguageProvider.get("gui.analysis.dependencies.install_jdk_error")
                                    .replace("$ERROR$", ex.getMessage()),
                            LanguageProvider.get("gui.analysis.dependencies.installation_error_title"),
                            JOptionPane.ERROR_MESSAGE);
                }
            });
            buttonPanel.add(installButton);

            JButton selectButton = new JButton(LanguageProvider.get("gui.analysis.dependencies.select_jdk_button"));
            selectButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this, LanguageProvider.get("gui.analysis.dependencies.specify_jdk_path"), LanguageProvider.get("gui.analysis.dependencies.select_jdk_title"), JOptionPane.INFORMATION_MESSAGE);
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fileChooser.setDialogTitle(LanguageProvider.get("gui.analysis.dependencies.select_jdk_directory"));
                fileChooser.setCurrentDirectory(new File("C:\\"));
                fileChooser.setPreferredSize(new Dimension(600, 400));

                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File selectedFolder = fileChooser.getSelectedFile();
                    String jdkPath = selectedFolder.getAbsolutePath();
                    CrashAssistantLocalConfig.set("JDK_PATH", jdkPath);
                    JOptionPane.showMessageDialog(this, LanguageProvider.get("gui.analysis.dependencies.success"), LanguageProvider.get("gui.analysis.dependencies.jdk_path_saved_title"), JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (parentDialog != null) {
                        parentDialog.dispose();
                    }
                }
            });
            buttonPanel.add(selectButton);

            JButton closeButton = new JButton(LanguageProvider.get("gui.close"));
            closeButton.addActionListener(e -> {
                dispose();
                if (parentDialog != null) {
                    parentDialog.dispose();
                }
            });
            buttonPanel.add(closeButton);
            add(buttonPanel, BorderLayout.SOUTH);

            setSize(600, 400);
            setLocationRelativeTo(parent);
        }
    }
}