package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Jdeps Dependencies Analysis: finds which mods depend on a given class or package name.
 * Supports simple and fully-qualified class names, as well as package paths.
 * Reuses jdeps resolution from DependenciesAnalysisGUIBase and supports nested-jar analysis.
 */
public class JdepsDependenciesAnalysisGUI extends DependenciesAnalysisGUIBase {
    @Override
    protected void recreateSelf() {
        new JdepsDependenciesAnalysisGUI((JFrame) dialog.getParent(), this.searchOriginal).start();
    }

    private final String searchOriginal;
    private final String targetClassPath;

    public JdepsDependenciesAnalysisGUI(JFrame parent, String className) {
        super(parent,
                LanguageProvider.get("gui.menu.analysis.jdeps_dependencies_analysis"),
                LanguageProvider.get("gui.analysis.jdeps.header"));
        this.searchOriginal = className.trim();
        String term = searchOriginal;
        if (term.toLowerCase().endsWith(".class")) term = term.substring(0, term.length() - 6);
        term = term.replace('.', '/');
        this.targetClassPath = term.toLowerCase() + ".class";
    }

    public static void showDialog(JFrame parent) {
        String lastSearch = (String) CrashAssistantLocalConfig.get("analysis.jdeps.last_search");

        String input = (String) JOptionPane.showInputDialog(
                parent,
                LanguageProvider.get("gui.analysis.jdeps.header") + "\n\n" + LanguageProvider.get("gui.analysis.jdeps.input_message"),
                LanguageProvider.get("gui.menu.analysis.jdeps_dependencies_analysis"),
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                lastSearch
        );

        if (input != null && !input.trim().isEmpty()) {
            CrashAssistantLocalConfig.set("analysis.jdeps.last_search", input.trim());
            new JdepsDependenciesAnalysisGUI(parent, input.trim()).start();
        }
    }

    @Override
    protected Predicate<String> isRelevantClass() {
        return name -> false;
    }

    @Override
    protected String getModId() {
        return "";
    }

    @Override
    protected String getModName() {
        return "";
    }

    private Predicate<String> buildPredicate() {
        String targetLower = targetClassPath;
        String targetLowerNoExt = targetLower.endsWith(".class") ? targetLower.substring(0, targetLower.length() - 6) : targetLower;
        CrashAssistantApp.LOGGER.info("Target class path: {}", targetLowerNoExt);
        int slashIdx = targetLower.lastIndexOf('/');
        String simpleWithExt = slashIdx >= 0 ? targetLower.substring(slashIdx + 1) : targetLower;
        String simple = simpleWithExt.endsWith(".class") ? simpleWithExt.substring(0, simpleWithExt.length() - 6) : simpleWithExt;
        final String simpleLower = simple.toLowerCase();
        final boolean userSpecifiedPackageOrFqn = searchOriginal.contains(".") || searchOriginal.contains("/");

        // Match logic (case-insensitive):
        // If user specified a package or FQN (contains dot or slash):
        //   - Exact FQN match (e.g., a/b/X.class)
        //   - Package prefix match (e.g., a/b/)
        //   - This mode DOES NOT match by simple class name only.
        // If user specified only a simple class name (no package):
        //   - Match by basename (e.g., Outer$Inner) and inner simple name (e.g., Inner)
        //   - Also allow exact FQN match just in case
        return classPath -> {
            String n = classPath.toLowerCase();
            if (!n.endsWith(".class")) n = n + ".class";

            // Always allow exact FQN path match
            if (n.equals(targetLower)) return true;

            if (userSpecifiedPackageOrFqn) {
                // User asked for package or fully qualified class name: respect package/class boundary
                // Match cases:
                // - Exact class FQN: a/b/X.class (handled above)
                // - Inner classes of that FQN: a/b/X$Inner.class
                // - Any class under the package: a/b/...
                return n.startsWith(targetLowerNoExt);
            }

            // User asked for simple name only: match by simple/inner names
            int lastSlash = n.lastIndexOf('/');
            String baseNoExt = n.substring(lastSlash + 1, n.length() - 6);
            if (baseNoExt.equals(simpleLower)) return true; // handles input like "X$Y" and also "X"

            int lastDollarInBase = baseNoExt.lastIndexOf('$');
            String innerSimple = lastDollarInBase >= 0 ? baseNoExt.substring(lastDollarInBase + 1) : baseNoExt;
            if (innerSimple.equals(simpleLower)) return true; // handles input like "Y" and also top-level "X"

            return false;
        };
    }

    @Override
    protected void performAnalysis() {
        String jdepsPath = getJDepsPath();
        if (jdepsPath == null) {
            SwingUtilities.invokeLater(() -> {
                showJdepsWarn((JFrame) dialog.getParent(), dialog);
                dialog.dispose();
            });
            return;
        }
        CrashAssistantApp.LOGGER.info("Using jdeps at: \"{}\"", jdepsPath);

        java.util.LinkedHashSet<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true);
        int totalMods = modsToAnalyze.size();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger foundAny = new AtomicInteger(0);
        java.util.List<String> matchedMods = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Predicate<String> predicate = buildPredicate();
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;
                SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + mod.getJarName()));

                JdepsScanResult scan = scanModWithJdeps(mod, jdepsPath, predicate, false);
                java.util.LinkedHashSet<String> displays = new java.util.LinkedHashSet<>(scan.depsByDisplay.keySet());
                // Fallback in case depsByDisplay is empty but a matchedDisplay was recorded
                if (displays.isEmpty() && scan.matchedDisplay != null) {
                    displays.add(scan.matchedDisplay);
                }
                if (!displays.isEmpty()) {
                    // Track this mod for FilesRemover (use base mod jar only, not nested jars)
                    registerDetectedModJar(mod.getJarName());
                }
                for (String display : displays) {
                    int idx = foundAny.getAndIncrement();
                    matchedMods.add(display);
                    CrashAssistantApp.LOGGER.info("Found dependency in {} for target class '{}'.", display, searchOriginal);
                    SwingUtilities.invokeLater(() -> {
                        if (idx == 0) {
                            String header = LanguageProvider.get("gui.analysis.jdeps.found").replace("$TERM$", searchOriginal);
                            appendStyledText(header, NORMAL_COLOR);
                        }
                        appendStyledText(display + "\n", MOD_COLOR);
                    });
                }

                int c = completed.incrementAndGet();
                SwingUtilities.invokeLater(() -> {
                    if (!isCancelled) progressBar.setValue(c);
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
            if (foundAny.get() == 0) {
                SwingUtilities.invokeLater(() -> {
                    String msg = LanguageProvider.get("gui.analysis.jdeps.not_found").replace("$TERM$", searchOriginal);
                    appendStyledText(msg, NORMAL_COLOR);
                });
                CrashAssistantApp.LOGGER.info("Haven't found in any mod dependencies on '{}'.", searchOriginal);
            } else {
                java.util.List<String> copy;
                synchronized (matchedMods) {
                    copy = new java.util.ArrayList<>(matchedMods);
                }
                copy.sort(String::compareTo);
                StringBuilder sb = new StringBuilder();
                sb.append("Mods depending on '").append(searchOriginal).append("':\n");
                for (String jar : copy) {
                    sb.append(jar).append('\n');
                }
                CrashAssistantApp.LOGGER.info(sb.toString().trim());
            }
        }
        // Cleanup temp directory after analysis completes
        if (isIncludeNestedEnabled()) {
            try {
                cleanJdepsTmp();
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.warn("Failed to clean jdeps tmp directory after analysis: {}", e.getMessage());
            }
        }
    }
}
