package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;

public class PiracyWarning extends JDialog {

    public static boolean isCurrentlyDisplayed = false;

    public PiracyWarning(Frame parent) {
        super(parent, LanguageProvider.get("gui.piracy_warning"), true);

        String content = LanguageProvider.get("warnings.piracy", new HashMap<String, String>() {{
            put("$LINK.PRISM$", "Prism Launcher");
        }}).replace("$HELP_NAME$", PlatformHelp.getActualHelpName());

        JEditorPane editorPane = CrashAssistantGUI.getEditorPane(content, false);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                )
        );
        textPanel.add(editorPane, BorderLayout.CENTER);

        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
        dontShowAgainCheck.addActionListener(e -> {
            CrashAssistantLocalConfig.set("piracy.dont_show_again", dontShowAgainCheck.isSelected());
            CrashAssistantApp.LOGGER.info("Piracy Warning Don't show again checkbox switched: {}", dontShowAgainCheck.isSelected());
        });

        JButton okButton = new JButton(LanguageProvider.get("gui.ok"));
        okButton.addActionListener(e -> dispose());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        bottomPanel.add(dontShowAgainCheck);
        bottomPanel.add(okButton);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(textPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        int delay = CrashAssistantConfig.getInteger("piracy.delay");

        if (delay > 0) {
            okButton.setEnabled(false);
            dontShowAgainCheck.setEnabled(false);
            setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

            final int[] secondsLeft = {delay};
            final int initialDelay = delay;
            okButton.setText(LanguageProvider.get("gui.ok") + " (" + secondsLeft[0] + ")");
            Timer timer = new Timer(1000, null);
            timer.addActionListener(e -> {
                secondsLeft[0]--;
                int elapsed = initialDelay - secondsLeft[0];
                if (elapsed >= 5 && getDefaultCloseOperation() == JDialog.DO_NOTHING_ON_CLOSE) {
                    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                }
                if (secondsLeft[0] <= 0) {
                    okButton.setText(LanguageProvider.get("gui.ok"));
                    okButton.setEnabled(true);
                    dontShowAgainCheck.setEnabled(true);
                    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    timer.stop();
                } else {
                    okButton.setText(LanguageProvider.get("gui.ok") + " (" + secondsLeft[0] + ")");
                }
            });
            timer.start();
        } else {
            okButton.setText(LanguageProvider.get("gui.ok"));
            okButton.setEnabled(true);
        }
    }

    public static void showWarning(Frame parent) {
        isCurrentlyDisplayed = true;
        SwingUtilities.invokeLater(() -> {
            CrashAssistantApp.LOGGER.warn("Showing PiracyWarning.");
            PiracyWarning dialog = new PiracyWarning(parent);
            CrashAssistantGUI.setUpIcon(dialog);

            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    CrashAssistantApp.LOGGER.warn("Shown PiracyWarning.");
                    isCurrentlyDisplayed = false;
                }
            });

            dialog.setVisible(true);
        });
        awaitShown();
    }

    public static void awaitShown() {
        while (isCurrentlyDisplayed) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
