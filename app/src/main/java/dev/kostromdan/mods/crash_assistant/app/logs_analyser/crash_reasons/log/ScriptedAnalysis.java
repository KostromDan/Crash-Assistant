package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;

public class ScriptedAnalysis extends KnownCrashReason {
    public ScriptedAnalysis(LogType logType, String message) {
        super(logType, message, new String[0]);
        priority = 10000;
    }

    public ScriptedAnalysis(LogType logType, ScriptWarning warning) {
        super(logType != null ? logType : LogType.LOG, warning.getMessage(), new String[0]);
        this.priority = warning.getPriority();
        this.dontShowAgainKey = warning.getDontShowAgainKey();
        this.customOkDelay = warning.getOkDelay();

        Mod mod = warning.getAffectedMod();
        if (mod != null) {
            String jarName = mod.getJarName();
            File modFile = ModListUtils.MODS_FOLDER.resolve(jarName).toFile();

            if (warning.isShowRemoveButton()) {
                String label = String.format(LanguageProvider.get("gui.remove_mod"), jarName);
                if (label.contains("%s")) label = label.replace("%s", jarName);
                if (label.equals("gui.remove_mod")) label = "Remove " + jarName;

                autoFixButtons.put(label, dialog -> {
                    try {
                        if (modFile.exists()) {
                            Files.delete(modFile.toPath());
                            JOptionPane.showMessageDialog(dialog, jarName + " removed. Please restart.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(dialog, "File not found: " + jarName, "Error", JOptionPane.ERROR_MESSAGE);
                        }
                        dialog.dispose();
                    } catch (IOException e) {
                        CrashAssistantApp.LOGGER.error("Failed to remove mod: " + jarName, e);
                        JOptionPane.showMessageDialog(dialog, "Error removing mod: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }

            if (warning.isShowDisableButton()) {
                String label = LanguageProvider.get("gui.disable_mod");
                autoFixButtons.put(label, dialog -> {
                    try {
                        if (modFile.exists()) {
                            File disabled = new File(modFile.getParent(), modFile.getName() + ".disabled");
                            Files.move(modFile.toPath(), disabled.toPath());
                            JOptionPane.showMessageDialog(dialog, jarName + " disabled. Please restart.", "Success", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(dialog, "File not found.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                        dialog.dispose();
                    } catch (FileAlreadyExistsException e) {
                        modFile.delete();
                        dialog.dispose();
                    } catch (IOException e) {
                        CrashAssistantApp.LOGGER.error("Failed to disable mod: " + jarName, e);
                        JOptionPane.showMessageDialog(dialog, "Error disabling mod: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }

            if (warning.isShowExplorerButton()) {
                String label = LanguageProvider.get("gui.show_in_explorer_button");
                autoFixButtons.put(label, dialog -> {
                    try {
                        if (System.getProperty("os.name").startsWith("Windows")) {
                            new ProcessBuilder("explorer.exe", "/select,", modFile.getAbsolutePath()).start();
                        } else {
                            Desktop.getDesktop().open(modFile.getParentFile());
                        }
                    } catch (Exception e) {
                        CrashAssistantApp.LOGGER.error("Failed to open explorer", e);
                    }
                });
            }
        }
    }


    /**
     * Legacy behavior: use message as the unique identifier for "Don't Show Again" and other config.
     * This avoids breaking old user configs or requiring new ID logic.
     */
    @Override
    public String getReasonName() {
        return message != null ? message : super.getReasonName();
    }

    @Override
    public boolean matches(dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log log) {
        return true;
    }
}
