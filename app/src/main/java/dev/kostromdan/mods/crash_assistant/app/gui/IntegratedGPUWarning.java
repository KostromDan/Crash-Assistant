package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JavaBinaryLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class IntegratedGPUWarning extends JFrame {

    public static boolean isCurrentlyDisplayed = false;

    /**
     * Constructs a new frame that displays an editor pane with warning messages,
     * and includes a "don't show again" checkbox and an OK button.
     *
     * @param integratedGPU the integrated GPU string to display
     * @param dedicatedGPUs the list of dedicated GPUs to display
     */
    public IntegratedGPUWarning(String integratedGPU, List<String> dedicatedGPUs) {
        super(LanguageProvider.get("gui.integrated_gpu"));

        // Prepare the warning text.
        String content = LanguageProvider.get("warnings.integrated_gpu")
                .replace("$I_GPU$", integratedGPU)
                .replace("$D_GPUS$", String.join("\n", dedicatedGPUs))
                .replace("$JAVA_PATH$", Path.of(JavaBinaryLocator.getJavaBinary(ProcessHandle.current()))
                        .toAbsolutePath().toString());

        // Editor pane with HTML content.
        JEditorPane editorPane = CrashAssistantGUI.getEditorPane(content, false);

        // Wrap the editor pane in a panel with a VISIBLE border + internal padding.
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),  // Visible line border
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)       // Spacing around the text
                )
        );
        textPanel.add(editorPane, BorderLayout.CENTER);

        // "Don't show again" checkbox.
        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
        dontShowAgainCheck.addActionListener(e -> {
                    CrashAssistantLocalConfig.set("integrated_gpu.dont_show_again", dontShowAgainCheck.isSelected());
                    CrashAssistantApp.LOGGER.info("Don't show again checkbox switched: {}", dontShowAgainCheck.isSelected());
                }
        );

        // OK button.
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dispose());

        // Bottom panel that centers both the checkbox and the OK button in the same row.
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.add(dontShowAgainCheck);
        bottomPanel.add(okButton);

        // Main panel to hold textPanel in the center and bottomPanel at the bottom.
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));  // Extra margin around everything
        mainPanel.add(textPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Set up the frame.
        setContentPane(mainPanel);
        pack();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
    }

    public static void showIfNotDisabled(String integratedGPU, List<String> dedicatedGPUs) {
        if (Objects.equals(CrashAssistantLocalConfig.get("integrated_gpu.dont_show_again"), true)) {
            CrashAssistantApp.LOGGER.warn("integrated_gpu.dont_show_again is true. Prevented GUI warn.");
            return;
        }
        isCurrentlyDisplayed = true;
        SwingUtilities.invokeLater(() -> {
            CrashAssistantApp.LOGGER.warn("Showing IntegratedGPUWarning.");
            IntegratedGPUWarning frame = new IntegratedGPUWarning(integratedGPU, dedicatedGPUs);

            // Add a window listener to wait for the frame to be closed
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    CrashAssistantApp.LOGGER.warn("Shown IntegratedGPUWarning."); // Log after frame is closed.
                    isCurrentlyDisplayed = false;
                }
            });

            frame.setVisible(true);
        });
    }

    public static void awaitShown(){
        while (isCurrentlyDisplayed) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    // Demo main method for testing.
    public static void main(String[] args) {
        // Show the warning with sample data.
        showIfNotDisabled(
                "Intel HD Graphics",
                List.of("NVIDIA GTX 1080", "AMD Radeon RX 580")
        );

    }
}
