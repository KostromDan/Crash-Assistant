package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.util.function.Predicate;

public class CreateDependenciesAnalysisGUI extends DependenciesAnalysisGUIBase {

    @Override
    protected void recreateSelf() {
        new CreateDependenciesAnalysisGUI((JFrame) dialog.getParent()).start();
    }

    public CreateDependenciesAnalysisGUI(JFrame parent) {
        super(parent, LanguageProvider.get("gui.analysis.create_dependencies.title"), LanguageProvider.get("gui.analysis.create_dependencies.header"));
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