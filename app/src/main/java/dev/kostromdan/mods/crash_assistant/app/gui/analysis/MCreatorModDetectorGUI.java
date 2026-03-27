package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;

public class MCreatorModDetectorGUI extends AnalysisGUIBase {

    public MCreatorModDetectorGUI(JFrame parent) {
        super(parent, LanguageProvider.get("gui.menu.analysis.mcreator_mod_detector"), LanguageProvider.get("gui.analysis.mcreator_detector.header"));
    }

    public static void showMCreatorModDetectorDialog(JFrame parent) {
        new MCreatorModDetectorGUI(parent).start();
    }

    @Override
    protected void performAnalysis() {
        LinkedHashSet<Mod> modsToAnalyze = ModListUtils.getCurrentModList(true);
        int totalMods = modsToAnalyze.size();
        executor.shutdown();
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        List<String> mcreatorModJarNames = new java.util.ArrayList<>();
        int completedTasks = 0;

        for (Mod mod : modsToAnalyze) {
            if (isCancelled) {
                return;
            }

            String jarName = mod.getJarName();
            SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + jarName));

            if (Boolean.TRUE.equals(mod.IsMCreator())) {
                mcreatorModJarNames.add(jarName);
                registerDetectedModJar(jarName);
            }

            completedTasks++;
            int completed = completedTasks;
            SwingUtilities.invokeLater(() -> {
                if (!isCancelled) {
                    progressBar.setValue(completed);
                }
            });
        }

        if (!isCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (mcreatorModJarNames.isEmpty()) {
                    appendStyledText(LanguageProvider.get("gui.analysis.mcreator_detector.no_mods"), NORMAL_COLOR);
                } else {
                    String msg = LanguageProvider.get("gui.analysis.mcreator_detector.found")
                            .replace("$COUNT$", String.valueOf(mcreatorModJarNames.size()));
                    appendStyledText(msg, NORMAL_COLOR);
                    for (String jarName : mcreatorModJarNames) {
                        appendStyledText(jarName + "\n", MOD_COLOR);
                    }
                }
            });
        }
    }


    @Override
    protected String getWhyButtonTextKey() {
        return "gui.analysis.mcreator_detector.why_button";
    }

    @Override
    protected String getWhyDialogTitleKey() {
        return "gui.analysis.mcreator_detector.about_title";
    }

    @Override
    protected String getWhyDialogBodyKey() {
        return "gui.analysis.mcreator_detector.why_info";
    }

    @Override
    protected int getWhyDialogWidth() {
        return 500;
    }
}
