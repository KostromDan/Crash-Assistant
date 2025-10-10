package dev.kostromdan.mods.crash_assistant.app.gui.analysis.gml;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.AnalysisGUIBase;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GroovyModLoaderAutoFixGUI extends AnalysisGUIBase {

    private static final Map<String, String> MCP_MAPPINGS = new HashMap<String, String>() {{ 
        put("1.20.1", "20230612.114412");
        put("1.19.4", "20230314.122934");
        put("1.19.3", "20221207.122022");
        put("1.19.2", "20220805.130853");
    }};

    public GroovyModLoaderAutoFixGUI(JFrame parent) {
        super(parent, LanguageProvider.get("gui.analysis.gml_autofix.title"), LanguageProvider.get("gui.analysis.gml_autofix.header"));
    }

    @Override
    protected void performAnalysis() {
        try {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(LanguageProvider.get("gui.analysis.gml_autofix.status.starting"));
                progressBar.setIndeterminate(true);
            });

            // --- Check for MCP Mappings ---
            String mcVersion = PlatformHelp.minecraftVersion;
            String mcpVersion = MCP_MAPPINGS.get(mcVersion);

            if (mcpVersion == null) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            dialog,
                            CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.analysis.gml_autofix.unsupported_version_error"), true),
                            LanguageProvider.get("gui.error"),
                            JOptionPane.ERROR_MESSAGE
                    );
                    statusLabel.setText(LanguageProvider.get("gui.analysis.gml_autofix.status.failed"));
                    progressBar.setIndeterminate(false);
                });
                return;
            }

            // --- Clean old GML cache ---
            SwingUtilities.invokeLater(() -> statusLabel.setText(LanguageProvider.get("gui.analysis.gml_autofix.status.cleaning_cache")));
            appendStyledText("Cleaning old GML cache...\n", NORMAL_COLOR);
            File gmlFolder = new File("mod_data/gml");
            if (gmlFolder.exists()) {
                FileUtils.deleteDirectory(gmlFolder);
                appendStyledText("Old GML cache folder deleted.\n", NORMAL_COLOR);
                CrashAssistantApp.LOGGER.info("Old GML cache folder deleted.");
            } else {
                appendStyledText("No old GML cache folder found to delete.\n", NORMAL_COLOR);
            }

            // --- Launch new downloader process ---
            SwingUtilities.invokeLater(() -> statusLabel.setText(LanguageProvider.get("gui.analysis.gml_autofix.status.downloading")));
            appendStyledText("Starting mapping downloader for Minecraft " + mcVersion + " with MCP " + mcpVersion + "\n", NORMAL_COLOR);
            CrashAssistantApp.LOGGER.info("Starting mapping downloader for Minecraft {} with MCP {}", mcVersion, mcpVersion);

            String classpath = System.getProperty("java.class.path");
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-Djava.net.preferIPv4Stack=true",
                    "-cp",
                    classpath,
                    "dev.kostromdan.mods.crash_assistant.app.gui.analysis.gml.GMLMappingDownloaderImpl",
                    mcVersion,
                    mcpVersion
            );

            Process process = pb.start();
            runningProcesses.add(process);

            // Capture and display output
            CompletableFuture<Void> stdOutFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String finalLine = line;
                        SwingUtilities.invokeLater(() -> appendStyledText(finalLine + "\n", NORMAL_COLOR));
                    }
                } catch (Exception e) {
                    if (!isCancelled) {
                        CrashAssistantApp.LOGGER.error("Error reading stdout from downloader", e);
                    }
                }
            }, executor);

            CompletableFuture<Void> stdErrFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String finalLine = line;
                        SwingUtilities.invokeLater(() -> appendStyledText(finalLine + "\n", ERROR_COLOR));
                    }
                } catch (Exception e) {
                    if (!isCancelled) {
                        CrashAssistantApp.LOGGER.error("Error reading stderr from downloader", e);
                    }
                }
            }, executor);

            CompletableFuture.allOf(stdOutFuture, stdErrFuture).join();

            int exitCode = process.waitFor();
            runningProcesses.remove(process);

            if (isCancelled) {
                appendStyledText("\nAnalysis cancelled by user.\n", ERROR_COLOR);
                return;
            }

            if (exitCode == 0) {
                appendStyledText("\nGML mapping download completed successfully.\n", MOD_COLOR);
                CrashAssistantApp.LOGGER.info("GML mapping download completed successfully.");
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    statusLabel.setText(LanguageProvider.get("gui.analysis.gml_autofix.status.success"));
                    JOptionPane.showMessageDialog(
                            dialog,
                            CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.analysis.gml_autofix.success_message"), true),
                            LanguageProvider.get("gui.success"),
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    dialog.dispose();
                });
            } else {
                throw new RuntimeException("Mapping downloader process exited with code: " + exitCode);
            }

        } catch (Exception e) {
            if (isCancelled) return;
            CrashAssistantApp.LOGGER.error("GML AutoFix failed", e);
            appendStyledText("\nError during AutoFix: " + e.getMessage() + "\n", ERROR_COLOR);
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                statusLabel.setText(LanguageProvider.get("gui.analysis.gml_autofix.status.failed"));
                String failureMessage = LanguageProvider.get("gui.analysis.gml_autofix.failure_message") + "\n" + e.getMessage();
                JOptionPane.showMessageDialog(
                        dialog,
                        CrashAssistantGUI.getEditorPane(failureMessage, true),
                        LanguageProvider.get("gui.error"),
                        JOptionPane.ERROR_MESSAGE
                );
            });
        }
    }

    public static void main(String[] args) {
        PlatformHelp.minecraftVersion = "1.20.1";

        SwingUtilities.invokeLater(() -> {
            // Create a dummy parent frame for the dialog
            JFrame testFrame = new JFrame("Test Frame");
            testFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            testFrame.setSize(100, 100);
            testFrame.setLocationRelativeTo(null);

            // Launch the auto-fix GUI
            new GroovyModLoaderAutoFixGUI(testFrame).start();
        });
    }
}
