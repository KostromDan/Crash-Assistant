package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import javax.swing.*;
import java.util.function.Predicate;

public class CreateDependenciesAnalysisGUI extends DependenciesAnalysisGUIBase {

    public CreateDependenciesAnalysisGUI(JFrame parent) {
        super(parent, "Create Dependencies Analysis", "Wait for analysis to finish.\nTry removing/updating/downgrading all <font color='red'>problematic mods</font> detected below to match the current <font color='#0000FF'>Create mod</font> version.");
    }

    public static void showCreateAnalysisDialog(JFrame parent) {
        new CreateDependenciesAnalysisGUI(parent).start();
    }

    @Override
    protected Predicate<String> isRelevantClass() {
        return className -> (className.startsWith("com/simibubi/create") ||
                className.startsWith("com/jozufozu/flywheel") ||
                className.startsWith("net/createmod") ||
                className.startsWith("dev/engine_room")) &&
                className.endsWith(".class");
    }

    @Override
    protected String getModId() {
        return "create";
    }

    @Override
    protected String getModName() {
        return "Create";
    }
}