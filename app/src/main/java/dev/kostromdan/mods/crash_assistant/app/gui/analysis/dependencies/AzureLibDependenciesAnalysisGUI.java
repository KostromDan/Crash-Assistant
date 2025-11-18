package dev.kostromdan.mods.crash_assistant.app.gui.analysis.dependencies;

import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import javax.swing.*;
import java.util.function.Predicate;

public class AzureLibDependenciesAnalysisGUI extends DependenciesAnalysisGUIBase {

    @Override
    protected void recreateSelf() {
        new AzureLibDependenciesAnalysisGUI((JFrame) dialog.getParent()).start();
    }

    public AzureLibDependenciesAnalysisGUI(JFrame parent) {
        super(parent, LanguageProvider.get("gui.analysis.azure_lib.title"), LanguageProvider.get("gui.analysis.azure_lib.header"));
    }

    public static void showAzureLibAnalysisDialog(JFrame parent) {
        new AzureLibDependenciesAnalysisGUI(parent).start();
    }

    @Override
    protected Predicate<String> isRelevantClass() {
        return className -> className.startsWith("mod/azure/azurelib") && className.endsWith(".class");
    }

    @Override
    protected String getModId() {
        return "azurelib";
    }

    @Override
    protected String getModName() {
        return "AzureLib";
    }
}