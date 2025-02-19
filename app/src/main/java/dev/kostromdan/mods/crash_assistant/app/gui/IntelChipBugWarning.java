package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.IntelCorruptedProcessorChecker;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class IntelChipBugWarning {
    public static final String HELP_URL = "https://www.zdnet.com/article/intel-chip-bug-faq-which-pcs-are-affected-how-to-get-the-patch-and-everything-else-you-need-to-know/";
    public static final String GIF_URL = "https://kostromdan.github.io/Crash-Assistant/assets/intel_bug.gif?raw=true";
    public static final Path LOCAL_GIF_PATH = Paths.get("local", "crash_assistant", "intel_bug.gif");

    public static void showIfAffected(boolean debug) {
        if (!CrashAssistantConfig.getBoolean("intel_corrupted.enabled")) return;
        if (!IntelCorruptedProcessorChecker.isAffectedProcessor() && !debug) return;
        if (Objects.equals(CrashAssistantLocalConfig.get("intel_corrupted.dont_show_again"), true)) return;
        boolean showGif = CrashAssistantConfig.getBoolean("intel_corrupted.show_gif");

        ControlPanel.stopMovingToTop = true;

        JDialog dialog = new JDialog((Frame) null, LanguageProvider.get("gui.intel_corrupted_title"), true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        int colIndex = 0;
        JLabel gifLabel = null;
        int gifWidth = 211;
        int gifHeight = 374;

        if (showGif) {
            JPanel gifPanel = new JPanel(new BorderLayout());
            gifPanel.setPreferredSize(new Dimension(gifWidth, gifHeight));
            gifPanel.setMinimumSize(new Dimension(gifWidth, gifHeight));
            gifPanel.setMaximumSize(new Dimension(gifWidth, gifHeight));
            gifPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

            gifLabel = new JLabel("GIF", SwingConstants.CENTER);
            gifLabel.setHorizontalAlignment(SwingConstants.CENTER);
            gifLabel.setVerticalAlignment(SwingConstants.CENTER);
            gifPanel.add(gifLabel, BorderLayout.CENTER);

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridheight = 2;
            gbc.weightx = 0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.VERTICAL;
            gbc.anchor = GridBagConstraints.CENTER;
            mainPanel.add(gifPanel, gbc);

            colIndex = 1;

            final JLabel finalGifLabel = gifLabel;
            new SwingWorker<ImageIcon, Void>() {
                @Override
                protected ImageIcon doInBackground() throws Exception {
                    LOCAL_GIF_PATH.getParent().toFile().mkdirs();

                    if (!LOCAL_GIF_PATH.toFile().isFile()) {
                        try (InputStream in = new URL(GIF_URL).openStream();
                             FileOutputStream out = new FileOutputStream(LOCAL_GIF_PATH.toFile())) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    }

                    Image image = Toolkit.getDefaultToolkit().createImage(LOCAL_GIF_PATH.toFile().getAbsolutePath());
                    MediaTracker tracker = new MediaTracker(new JPanel());
                    tracker.addImage(image, 0);
                    tracker.waitForAll();
                    return new ImageIcon(image);
                }

                @Override
                protected void done() {
                    try {
                        ImageIcon loadedIcon = get();
                        finalGifLabel.setText(null);
                        finalGifLabel.setIcon(loadedIcon);
                    } catch (Exception e) {
                        CrashAssistantApp.LOGGER.error("Error loading GIF: ", e);
                        finalGifLabel.setText("Failed to load GIF");
                    }
                }
            }.execute();
        }

        JEditorPane textPane = CrashAssistantGUI.getEditorPane(
                LanguageProvider.get("gui.intel_corrupted_msg").replace("$HELP_URL$", HELP_URL), true);
        textPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        gbc.gridx = colIndex;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(textPane, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        JButton readMoreButton = new JButton(LanguageProvider.get("gui.intel_corrupted_read_more"));
        readMoreButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(HELP_URL));
            } catch (Exception ex) {
                CrashAssistantApp.LOGGER.error("Error opening URL: ", ex);
            }
        });
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(readMoreButton);
        buttonPanel.add(okButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
        dontShowAgainCheck.addActionListener(e ->
                CrashAssistantLocalConfig.set("intel_corrupted.dont_show_again", dontShowAgainCheck.isSelected())
        );
        bottomPanel.add(dontShowAgainCheck, BorderLayout.WEST);

        gbc.gridx = colIndex;
        gbc.gridy = 1;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(bottomPanel, gbc);

        dialog.setContentPane(mainPanel);
        dialog.pack();
        dialog.setSize(800 - (showGif ? 0 : gifWidth), Math.max(dialog.getPreferredSize().height, 439));
        dialog.setLocationRelativeTo(null);
        if (debug) dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        IntelChipBugWarning.showIfAffected(true);
    }
}
