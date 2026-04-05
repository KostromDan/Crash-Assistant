package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.ComparableVersion;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DuplicatedCrashAssistantMod extends KnownCrashReason {
    private static final String CRASH_ASSISTANT_MOD_ID = "crash_assistant";

    private final List<Mod> fixCandidates;
    private final boolean compareByVersion;

    public DuplicatedCrashAssistantMod(List<Mod> duplicatedMods) {
        super(LogType.LOG, buildMessage(duplicatedMods), new String[0]);
        priority = 10000;
        customOkDelay = 0;

        fixCandidates = getFixCandidates(duplicatedMods);
        compareByVersion = fixCandidates.stream()
                .allMatch(mod -> mod.getVersion() != null && !mod.getVersion().trim().isEmpty());
        autoFixButtons.put(
                LanguageProvider.get("gui.duplicated_mod_autofix_keep_latest"),
                this::autoFixKeepOnlyLatest
        );
    }

    private static String buildMessage(List<Mod> duplicatedMods) {
        String mods = duplicatedMods.stream()
                .map(Mod::getJarName)
                .collect(Collectors.joining("\n"));
        return LanguageProvider.get("gui.duplicated_mod_warn")
                .replace("$MODS$", mods);
    }

    private static List<Mod> getFixCandidates(List<Mod> duplicatedMods) {
        List<Mod> exactModIdMatches = duplicatedMods.stream()
                .filter(mod -> CRASH_ASSISTANT_MOD_ID.equalsIgnoreCase(mod.getModId()))
                .collect(Collectors.toList());

        if (exactModIdMatches.size() > 1) {
            return exactModIdMatches;
        }
        return new ArrayList<>(duplicatedMods);
    }

    private void autoFixKeepOnlyLatest(JDialog parentDialog) {
        if (fixCandidates.size() < 2) {
            return;
        }

        Mod modToKeep = getLatestVersionMod();
        List<Path> filesToDelete = fixCandidates.stream()
                .filter(mod -> !Objects.equals(mod.getJarName(), modToKeep.getJarName()))
                .map(mod -> ModListUtils.MODS_FOLDER.resolve(mod.getJarName()).normalize())
                .distinct()
                .collect(Collectors.toList());

        if (filesToDelete.isEmpty()) {
            return;
        }

        int response = JOptionPane.showConfirmDialog(
                parentDialog,
                CrashAssistantGUI.getEditorPane(
                        LanguageProvider.get("gui.duplicated_mod_autofix_confirm")
                                .replace("$MOD$", modToKeep.getJarName()),
                        false
                ),
                LanguageProvider.get("gui.duplicated_mod"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (response != JOptionPane.YES_OPTION) {
            return;
        }

        List<Path> failedFiles = new ArrayList<>();
        for (Path path : filesToDelete) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                failedFiles.add(path);
                CrashAssistantApp.LOGGER.error("Failed to delete duplicated crash assistant mod file: {}", path, e);
            }
        }

        if (failedFiles.isEmpty()) {
            CrashAssistantApp.LOGGER.info("Duplicated Crash Assistant auto-fix completed. Kept: {}", modToKeep.getJarName());
            JOptionPane.showMessageDialog(
                    parentDialog,
                    CrashAssistantGUI.getEditorPane(
                            LanguageProvider.get("gui.duplicated_mod_autofix_success")
                                    .replace("$MOD$", modToKeep.getJarName()),
                            false
                    ),
                    LanguageProvider.get("gui.duplicated_mod"),
                    JOptionPane.INFORMATION_MESSAGE
            );
            parentDialog.dispose();
            return;
        }

        String failedFilesString = failedFiles.stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.joining("\n"));
        JOptionPane.showMessageDialog(
                parentDialog,
                CrashAssistantGUI.getEditorPane(
                        LanguageProvider.get("gui.duplicated_mod_autofix_partial_failure")
                                .replace("$FAILED_FILES$", failedFilesString),
                        false
                ),
                LanguageProvider.get("gui.error"),
                JOptionPane.ERROR_MESSAGE
        );
    }

    private Mod getLatestVersionMod() {
        return fixCandidates.stream()
                .max(this::compareMods)
                .orElse(fixCandidates.get(0));
    }

    private int compareMods(Mod left, Mod right) {
        if (left.getVersion() != null && right.getVersion() != null && left.getVersion().equals(right.getVersion())) {
            return right.getJarName().length() - left.getJarName().length();
        }
        String leftSource = compareByVersion ? left.getVersion() : left.getJarName();
        String rightSource = compareByVersion ? right.getVersion() : right.getJarName();
        ComparableVersion leftVersion = new ComparableVersion(leftSource == null ? "" : leftSource);
        ComparableVersion rightVersion = new ComparableVersion(rightSource == null ? "" : rightSource);
        return leftVersion.compareTo(rightVersion);
    }
}
