package dev.kostromdan.mods.crash_assistant.app.gui;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;
import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;


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

        // Auto-Fix button.
        JButton autoFixButton = new JButton(LanguageProvider.get("gui.integrated_gpu_autofix_button"));
        autoFixButton.addActionListener(e -> {
            String javaPath = JavaBinaryLocator.getJavaBinary();

            String confirmationMessage = LanguageProvider.get("gui.integrated_gpu_autofix_confirm_message")
                    .replace("$JAVA_PATH$", javaPath);

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    CrashAssistantGUI.getEditorPane(confirmationMessage, true, 500),
                    LanguageProvider.get("gui.integrated_gpu_autofix_confirm_title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                autoFixButton.setEnabled(false);
                autoFixButton.setText(LanguageProvider.get("gui.integrated_gpu_autofix_working"));
                new Thread(() -> {
                    String result = applyGpuPreference(javaPath);
                    SwingUtilities.invokeLater(() -> {
                        if ("SUCCESS".equals(result)) {
                            JOptionPane.showMessageDialog(
                                    this,
                                    LanguageProvider.get("gui.integrated_gpu_autofix_success"),
                                    LanguageProvider.get("gui.integrated_gpu_autofix_result_title"),
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                            autoFixButton.setText(LanguageProvider.get("gui.integrated_gpu_autofix_done"));
                            dontShowAgainCheck.setEnabled(false);
                            dontShowAgainCheck.setSelected(false);
                        } else {
                            String failureMessage = LanguageProvider.get("gui.integrated_gpu_autofix_failure")
                                    .replace("$ERROR$", result);
                            JOptionPane.showMessageDialog(
                                    this,
                                    failureMessage,
                                    LanguageProvider.get("gui.integrated_gpu_autofix_result_title"),
                                    JOptionPane.ERROR_MESSAGE
                            );
                            autoFixButton.setEnabled(true);
                            autoFixButton.setText(LanguageProvider.get("gui.integrated_gpu_autofix_button"));
                        }
                    });
                }).start();
            }
        });

        // Bottom panel that centers both the checkbox and the OK button in the same row.
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.add(dontShowAgainCheck);
        bottomPanel.add(autoFixButton);
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

    /**
     * A local interface to hold missing constants for compatibility with older JNA versions.
     */
    private interface RegAuth {
        int KEY_SET_VALUE = 0x0002;
        int REG_OPTION_NON_VOLATILE = 0x0000;
        int REG_SZ = 1;
    }

    /**
     * Sets the GPU preference for the specified Java executable to "High performance".
     * This is done by writing directly to the Windows Registry using JNA.
     * This method does not require administrator rights.
     *
     * @param javaPath The absolute path to the javaw.exe file.
     * @return "SUCCESS" if the operation completes without errors, otherwise an error message.
     */
    public static String applyGpuPreference(String javaPath) {
        final String keyPath = "Software\\Microsoft\\DirectX\\UserGpuPreferences";
        final String valueData = "GpuPreference=2;";

        WinReg.HKEYByReference phkResult = null;
        try {
            phkResult = new WinReg.HKEYByReference();

            // Try to open the key first.
            int openResult = Advapi32.INSTANCE.RegOpenKeyEx(
                    WinReg.HKEY_CURRENT_USER,
                    keyPath,
                    0,
                    RegAuth.KEY_SET_VALUE,
                    phkResult
            );

            // If the key doesn't exist, create it.
            if (openResult == WinError.ERROR_FILE_NOT_FOUND) {
                int createResult = Advapi32.INSTANCE.RegCreateKeyEx(
                        WinReg.HKEY_CURRENT_USER,
                        keyPath,
                        0,
                        null,
                        RegAuth.REG_OPTION_NON_VOLATILE,
                        RegAuth.KEY_SET_VALUE,
                        null,
                        phkResult,
                        null
                );
                if (createResult != WinError.ERROR_SUCCESS) {
                    throw new Win32Exception(createResult);
                }
            } else if (openResult != WinError.ERROR_SUCCESS) {
                throw new Win32Exception(openResult);
            }

            // Now, set the string value.
            int setResult = Advapi32.INSTANCE.RegSetValueEx(
                    phkResult.getValue(),
                    javaPath,
                    0,
                    RegAuth.REG_SZ,
                    valueData.toCharArray(),
                    (valueData.length() + 1) * 2
            );

            if (setResult != WinError.ERROR_SUCCESS) {
                throw new Win32Exception(setResult);
            }

            return "SUCCESS";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        } finally {
            // Always close the registry key handle.
            if (phkResult != null && phkResult.getValue() != null) {
                Advapi32.INSTANCE.RegCloseKey(phkResult.getValue());
            }
        }
    }

    // Demo main method for testing.
    public static void main(String[] args) {
        // Show the warning with sample data.
        show(
                "Intel HD Graphics",
                Arrays.asList("NVIDIA GTX 1080", "AMD Radeon RX 580")
        );

    }
}
