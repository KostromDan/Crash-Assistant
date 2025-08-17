package dev.kostromdan.mods.crash_assistant.app.gui.analysis;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        AtomicInteger completedTasks = new AtomicInteger(0);
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(totalMods));

        List<Mod> mcreatorMods = new java.util.ArrayList<>();

        for (Mod mod : modsToAnalyze) {
            executor.submit(() -> {
                if (isCancelled) return;

                SwingUtilities.invokeLater(() -> currentJarLabel.setText(LanguageProvider.get("gui.analysis.current_mod") + " " + mod.getJarName()));

                if (Boolean.TRUE.equals(mod.IsMCreator())) {
                    mcreatorMods.add(mod);
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
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!isCancelled) {
            SwingUtilities.invokeLater(() -> {
                if (mcreatorMods.isEmpty()) {
                    appendStyledText(LanguageProvider.get("gui.analysis.mcreator_detector.no_mods"), NORMAL_COLOR);
                } else {
                    String msg = LanguageProvider.get("gui.analysis.mcreator_detector.found")
                            .replace("$COUNT$", String.valueOf(mcreatorMods.size()));
                    appendStyledText(msg, NORMAL_COLOR);
                    for (Mod mod : mcreatorMods) {
                        appendStyledText(mod.getJarName() + "\n", MOD_COLOR);
                    }
                }
            });
        }
    }


    @Override
    protected void addOkButton() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton whyButton = new JButton(LanguageProvider.get("gui.analysis.mcreator_detector.why_button"));
        whyButton.addActionListener(e -> {
            String infoMessage = LanguageProvider.get("gui.analysis.mcreator_detector.why_info");

            JEditorPane infoPane = dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI.getEditorPane(infoMessage, true, 500);

            JOptionPane.showMessageDialog(
                    dialog,
                    infoPane,
                    LanguageProvider.get("gui.analysis.mcreator_detector.about_title"),
                    JOptionPane.INFORMATION_MESSAGE
            );
        });
        buttonPanel.add(whyButton);

        JButton okButton = new JButton(LanguageProvider.get("gui.ok"));
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);

        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.revalidate();
    }
}