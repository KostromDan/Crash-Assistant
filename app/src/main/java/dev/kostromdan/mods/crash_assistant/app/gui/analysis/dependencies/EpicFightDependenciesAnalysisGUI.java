package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import javax.swing.*;
import java.util.function.Predicate;

public class EpicFightDependenciesAnalysisGUI extends DependenciesAnalysisGUIBase {

    public EpicFightDependenciesAnalysisGUI(JFrame parent) {
        super(parent, "Epic Fight Dependencies Analysis", "Wait for analysis to finish.\nTry removing/updating/downgrading all <font color='red'>problematic mods</font> detected below to match the current <font color='#0000FF'>Epic Fight mod</font> version.");
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