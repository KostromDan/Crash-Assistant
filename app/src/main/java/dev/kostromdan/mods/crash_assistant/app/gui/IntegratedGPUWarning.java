package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
                .replace("$JAVA_PATH$", Paths.get(JavaBinaryLocator.getJavaBinary())
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

//        // Auto-Fix button.
//        JButton autoFixButton = new JButton(LanguageProvider.get("gui.integrated_gpu_autofix_button"));
//        autoFixButton.addActionListener(e -> {
//            String javaPath = JavaBinaryLocator.getJavaBinary();
//            String commandToExecute = getGpuPreferenceCommand(javaPath);
//            // Use HTML tags for better formatting in the dialog
//            String formattedCommand = "<code style='background-color: #d1e7ff; padding: 2px 4px;'>" + commandToExecute.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</code>";
//
//            String confirmationMessage = LanguageProvider.get("gui.integrated_gpu_autofix_confirm_message")
//                    .replace("$COMMAND$", formattedCommand);
//
//            int choice = JOptionPane.showConfirmDialog(
//                    this,
//                    CrashAssistantGUI.getEditorPane(confirmationMessage, true, 500),
//                    LanguageProvider.get("gui.integrated_gpu_autofix_confirm_title"),
//                    JOptionPane.YES_NO_OPTION,
//                    JOptionPane.QUESTION_MESSAGE
//            );
//
//            if (choice == JOptionPane.YES_OPTION) {
//                autoFixButton.setEnabled(false);
//                autoFixButton.setText(LanguageProvider.get("gui.integrated_gpu_autofix_working"));
//                new Thread(() -> {
//                    String result = applyGpuPreference(javaPath);
//                    SwingUtilities.invokeLater(() -> {
//                        if ("SUCCESS".equals(result)) {
//                            JOptionPane.showMessageDialog(
//                                    this,
//                                    LanguageProvider.get("gui.integrated_gpu_autofix_success"),
//                                    LanguageProvider.get("gui.integrated_gpu_autofix_result_title"),
//                                    JOptionPane.INFORMATION_MESSAGE
//                            );
//                            autoFixButton.setText(LanguageProvider.get("gui.integrated_gpu_autofix_done"));
//                            dontShowAgainCheck.setEnabled(false);
//                            dontShowAgainCheck.setSelected(false);
//                        } else {
//                            String failureMessage = LanguageProvider.get("gui.integrated_gpu_autofix_failure")
//                                    .replace("$ERROR$", result);
//                            JOptionPane.showMessageDialog(
//                                    this,
//                                    failureMessage,
//                                    LanguageProvider.get("gui.integrated_gpu_autofix_result_title"),
//                                    JOptionPane.ERROR_MESSAGE
//                            );
//                            autoFixButton.setEnabled(true);
//                            autoFixButton.setText(LanguageProvider.get("gui.integrated_gpu_autofix_button"));
//                        }
//                    });
//                }).start();
//            }
//        });

        // Bottom panel that centers both the checkbox and the OK button in the same row.
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.add(dontShowAgainCheck);
//        bottomPanel.add(autoFixButton);
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

    public static void show(String integratedGPU, List<String> dedicatedGPUs) {
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


//    /**
//     * Generates the PowerShell command string needed to set the GPU preference.
//     *
//     * @param javaPath The absolute path to the javaw.exe file.
//     * @return The PowerShell command as a single-line string.
//     */
//    public static String getGpuPreferenceCommand(String javaPath) {
//        // PowerShell command to set GPU preference in Windows registry for Java executable
//        // This modifies the same registry values that Windows GUI changes when setting GPU preferences.
//        return String.format(
//                "& { try { if (-not (Test-Path -Path 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences')) { New-Item -Path 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences' -Force | Out-Null; } " +
//                        "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\DirectX\\UserGpuPreferences' -Name '%s' -Value 'GpuPreference=2;' -Force; return 'SUCCESS'; } catch { return \"ERROR: $($_.Exception.Message)\"; } }",
//                javaPath
//        );
//    }

//    /**
//     * Executes the PowerShell command to apply the high-performance GPU preference.
//     *
//     * @param javaPath The absolute path to the javaw.exe file.
//     * @return "SUCCESS" if the operation completes, or an error message if it fails.
//     */
//    public static String applyGpuPreference(String javaPath) {
//        String psCommand = getGpuPreferenceCommand(javaPath);
//
//        try {
//            // Use ProcessBuilder to run PowerShell with the command
//            ProcessBuilder builder = new ProcessBuilder(
//                    "powershell.exe",
//                    "-NoProfile",
//                    "-ExecutionPolicy", "Bypass",
//                    "-Command", psCommand
//            );
//            Process process = builder.start();
//
//            // Read the output from PowerShell to get the SUCCESS or ERROR message
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                String result = reader.lines().collect(Collectors.joining("\n")).trim();
//                int exitCode = process.waitFor();
//                if (exitCode == 0 && result.equals("SUCCESS")) {
//                    return "SUCCESS";
//                }
//                return result.isEmpty() ? "ERROR: Unknown PowerShell execution error." : result;
//            }
//        } catch (IOException | InterruptedException e) {
//            Thread.currentThread().interrupt(); // Restore interrupted status
//            return "ERROR: " + e.getMessage();
//        }
//    }

    // Demo main method for testing.
    public static void main(String[] args) {
        // Show the warning with sample data.
        show(
                "Intel HD Graphics",
                Arrays.asList("NVIDIA GTX 1080", "AMD Radeon RX 580")
        );

    }
}
