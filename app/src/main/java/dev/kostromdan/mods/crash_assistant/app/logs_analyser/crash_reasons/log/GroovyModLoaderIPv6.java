package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.gui.CrashAssistantGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies.JdepsDependenciesAnalysisGUI;
import dev.kostromdan.mods.crash_assistant.app.gui.analysis.gml.GroovyModLoaderAutoFixGUI;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.util.List;

public class GroovyModLoaderIPv6 extends KnownCrashReason {
    public GroovyModLoaderIPv6() {
        super(
                LogType.CRASH_REPORT,
                LanguageProvider.get("warnings.groovy_mod_loader_ipv6")
        );

        autoFixButtons.put(LanguageProvider.get("gui.analysis.gml_autofix.button"), parentDialog -> {
            int response = JOptionPane.showConfirmDialog(
                    parentDialog,
                    CrashAssistantGUI.getEditorPane(LanguageProvider.get("gui.analysis.gml_autofix.confirm_message"), true),
                    LanguageProvider.get("gui.analysis.gml_autofix.confirm_title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (response == JOptionPane.YES_OPTION) {
                parentDialog.dispose();
                JFrame rootFrame = (JFrame) SwingUtilities.getWindowAncestor(parentDialog);
                new GroovyModLoaderAutoFixGUI(rootFrame).start();
            }
        });
        autoFixButtons.put(LanguageProvider.get("gui.analysis.gml_autofix.find_mods"), (dialog) -> {
            new JdepsDependenciesAnalysisGUI((JFrame) dialog.getOwner(), "org.groovymc.gml").start();
        });
    }

    @Override
    public boolean matches(Log log) {
        List<String> lines = log.getReader().getAllLinesList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("at ") || !line.contains(".gml.mappings.Mapping")) {
                continue;
            }
            if (line.startsWith("at org.groovymc.gml.mappings.MappingsProvider.downloadFile(MappingsProvider.groovy:") ||
                    line.startsWith("at org.groovymc.gml.mappings.MappingsProvider.loadLayeredMappings(MappingsProvider.groovy:") ||
                    line.startsWith(("at com.matyrobbrt.gml.mappings.MappingMetaClassCreationHandle.applyCreationHandle(MappingMetaClassCreationHandle.groovy:"))) {
                return true;
            }
        }
        return false;
    }
}