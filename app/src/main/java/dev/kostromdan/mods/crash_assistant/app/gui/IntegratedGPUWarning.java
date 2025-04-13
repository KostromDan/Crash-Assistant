package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JavaBinaryLocator;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class IntegratedGPUWarning extends JFrame {

    /**
     * Constructs a new frame that displays an editor pane with warning messages,
     * and includes a "don't show again" checkbox, a "Learn more" button, and an OK button.
     *
     * @param integratedGPU the integrated GPU string to display
     * @param dedicatedGPUs the list of dedicated GPUs to display
     */
    public IntegratedGPUWarning(String integratedGPU, List<String> dedicatedGPUs) {
        super("Integrated GPU Warning");

        // Build the content text for the editor pane using the provided parameters.
        String content = LanguageProvider.get("warnings.integrated_gpu")
                .replace("$I_GPU$", integratedGPU)
                .replace("$D_GPUS$", String.join("\n", dedicatedGPUs))
                .replace("$JAVA_PATH$", Path.of(JavaBinaryLocator.getJavaBinary(ProcessHandle.current()))
                        .toAbsolutePath().toString());

        // Create the editor pane with your custom text.
        // If CrashAssistantGUI.getEditorPane returns a scrollable pane, you can add that directly.
        // Otherwise, consider wrapping it in a JScrollPane if the content can be long.
        JEditorPane editorPane = CrashAssistantGUI.getEditorPane(content, false);
        editorPane.setEditable(false);

        // ---------------------------------------------
        // Bottom panel components
        // ---------------------------------------------

        // 1. "Don't show again" checkbox (left).
        JCheckBox dontShowAgainCheck = new JCheckBox(LanguageProvider.get("gui.intel_corrupted_dont_show_again"));
        dontShowAgainCheck.addActionListener(e ->
                CrashAssistantLocalConfig.set("intel_corrupted.dont_show_again", dontShowAgainCheck.isSelected())
        );

        // 2. "Learn more" button (center).
        JButton learnMoreButton = new JButton(("gui.learn_more"));
        learnMoreButton.addActionListener(e -> {
            // TODO: Open FAQ link or documentation in a browser, for example:
            // openLink("https://example.com/intel-bug-faq");
            // For now, just show a message dialog as a placeholder.
            JOptionPane.showMessageDialog(
                    this,
                    "Here, you could show more information or open a browser to the FAQ page.",
                    "Learn More",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });

        // 3. OK button (right).
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dispose());

        // ---------------------------------------------
        // Layout the bottom panel to match your screenshot
        // ---------------------------------------------
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Left side: "Don't show again"
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.add(dontShowAgainCheck);
        bottomPanel.add(leftPanel, BorderLayout.WEST);

        // Center: "Learn more"
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerPanel.add(learnMoreButton);
        bottomPanel.add(centerPanel, BorderLayout.CENTER);

        // Right: "OK"
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.add(okButton);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        // ---------------------------------------------
        // Frame layout
        // ---------------------------------------------
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(editorPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    // Example main method for testing the frame independently.
    public static void main(String[] args) {
        // Set the system look and feel if possible.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Launch the GUI on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            // Provide sample data for testing.
            IntegratedGPUWarning frame = new IntegratedGPUWarning(
                    "Intel HD Graphics",
                    List.of("NVIDIA GTX 1080", "AMD Radeon RX 580")
            );
            frame.setVisible(true);
        });
    }
}
