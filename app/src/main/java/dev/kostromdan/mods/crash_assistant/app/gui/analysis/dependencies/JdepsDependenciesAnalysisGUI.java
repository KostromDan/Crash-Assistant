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
 * Jdeps Dependencies Analysis: finds which mods depend on a given class name (simple or fully-qualified).
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
        int slashIdx = targetLower.lastIndexOf('/');
        String simpleWithExt = slashIdx >= 0 ? targetLower.substring(slashIdx + 1) : targetLower;
        String simple = simpleWithExt.endsWith(".class") ? simpleWithExt.substring(0, simpleWithExt.length() - 6) : simpleWithExt;
        final String simpleLower = simple.toLowerCase();

        // Match logic supports all of the following inputs (case-insensitive):
        // - X       => match any class whose simple name is X (top-level or inner part after '$')
        // - X$Y     => match classes whose basename is exactly X$Y (ignoring package)
        // - Y       => match inner classes whose inner simple name is Y, and top-level Y
        // - Full FQN like a.b.X$Y or a/b/X$Y.class => exact FQN path match
        return classPath -> {
            String n = classPath.toLowerCase();
            if (!n.endsWith(".class")) n = n + ".class";
            // 1) Exact FQN path match
            if (n.equals(targetLower)) return true;

            // 2) Basename (file name without package and extension), e.g., "outer$inner"
            int lastSlash = n.lastIndexOf('/');
            String baseNoExt = n.substring(lastSlash + 1, n.length() - 6);
            if (baseNoExt.equals(simpleLower)) return true; // handles input like "X$Y" and also "X"

            // 3) Simple name after the last '$' (inner simple), e.g., "inner" in "outer$inner"
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

                JdepsScanResult scan = scanModWithJdeps(mod, jdepsPath, predicate, true);
                if (scan.matched) {
                    int first = foundAny.getAndIncrement();
                    String display = scan.matchedDisplay != null ? scan.matchedDisplay : mod.getJarName();
                    matchedMods.add(display);
                    CrashAssistantApp.LOGGER.info("Found dependency in {} for target class '{}'.", display, searchOriginal);
                    SwingUtilities.invokeLater(() -> {
                        if (first == 0) {
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
    }
}
