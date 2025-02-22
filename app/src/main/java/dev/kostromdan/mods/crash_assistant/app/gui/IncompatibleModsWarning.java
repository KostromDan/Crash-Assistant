package dev.kostromdan.mods.crash_assistant.app.gui;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.config.ProblematicModsConfig;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;

public class IncompatibleModsWarning {
    /**
     * Displays warnings for incompatible mods, allowing the user to remove, disable, show in explorer, or dismiss each mod.
     *
     * @param parent The parent component for the dialog (can be null for centered dialogs)
     */
    public static void showWarnings(Component parent) {
        ControlPanel.stopMovingToTop = true;

        synchronized (KnownCrashReason.class) {
            try {
                List<ProblematicModsConfig.ProblematicMod> currentProblematicMods = ProblematicModsConfig.getCurrentProblematicMods();

                SwingUtilities.invokeAndWait(() -> {
                    for (ProblematicModsConfig.ProblematicMod problematicMod : currentProblematicMods) {
                        String[] options = {
                                LanguageProvider.get("gui.remove_mod"),
                                LanguageProvider.get("gui.disable_mod"),
                                LanguageProvider.get("gui.show_in_explorer_button"),
                                LanguageProvider.get("gui.ok")
                        };

                        int choice = JOptionPane.showOptionDialog(
                                parent,
                                CrashAssistantGUI.getEditorPane(
                                        (problematicMod.msg()).replaceAll("\\$JAR_NAME\\$", problematicMod.currentMod().getJarName()),
                                        false
                                ),
                                LanguageProvider.get("gui.incompatible_mod"),
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.WARNING_MESSAGE,
                                null,
                                options,
                                options[3]
                        );
                        File file = Paths.get("mods", problematicMod.currentMod().getJarName()).toFile();
                        if (choice == 0) { // Remove mod
                            try {
                                Files.delete(file.toPath());
                            } catch (NoSuchFileException ignored) {
                            } catch (Exception e) {
                                CrashAssistantApp.LOGGER.error("Failed to remove mod: ", e);
                                JOptionPane.showMessageDialog(
                                        parent,
                                        String.format(LanguageProvider.get("gui.error_delete_mod_exception"), e),
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE
                                );
                            }
                        } else if (choice == 1) { // Disable mod
                            File disabledFile = new File(file.getParent(), file.getName() + ".disabled");
                            try {
                                Files.move(file.toPath(), disabledFile.toPath());
                            } catch (FileAlreadyExistsException e) {
                                file.delete();
                            } catch (IOException e) {
                                CrashAssistantApp.LOGGER.error("Failed to disable mod: ", e);
                                JOptionPane.showMessageDialog(
                                        parent,
                                        String.format(LanguageProvider.get("gui.error_disable_mod_exception"), e),
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE
                                );
                            }
                        } else if (choice == 2) { // Show in explorer
                            try {
                                if (System.getProperty("os.name").startsWith("Windows")) {
                                    new ProcessBuilder("explorer.exe", "/select,", file.toPath().toAbsolutePath().toString()).start();
                                } else {
                                    Desktop.getDesktop().open(file.getParentFile());
                                }
                            } catch (Exception e) {
                                CrashAssistantApp.LOGGER.error("Failed to show file in explorer: ", e);
                                JOptionPane.showMessageDialog(
                                        parent,
                                        String.format(LanguageProvider.get("gui.error_open_explorer"), e),
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE
                                );
                            }
                        }
                    }
                });
            } catch (Exception e) {
                CrashAssistantApp.LOGGER.error("Error while showing incompatible mods warnings: ", e);
            }
        }
    }
}