package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.util.function.Predicate;

public class EpicFightDependenciesAnalysisGUI extends DependenciesAnalysisGUIBase {

    public EpicFightDependenciesAnalysisGUI(JFrame parent) {
        super(parent, LanguageProvider.get("gui.analysis.epicfight.title"), LanguageProvider.get("gui.analysis.epicfight.header"));
    }

    public static void showEpicFightAnalysisDialog(JFrame parent) {
        new EpicFightDependenciesAnalysisGUI(parent).start();
    }

    @Override
    protected Predicate<String> isRelevantClass() {
        return className -> className.startsWith("yesman/epicfight") && className.endsWith(".class");
    }

    @Override
    protected String getModId() {
        return "epicfight";
    }

    @Override
    protected String getModName() {
        return "Epic Fight";
    }
}