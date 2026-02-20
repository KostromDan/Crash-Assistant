package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import org.apache.commons.jexl3.JexlContext;

import java.nio.file.Path;
import java.nio.file.Paths;


public class StartupScriptManager extends AbstractScriptManager {

    private static final StartupScriptManager INSTANCE = new StartupScriptManager();

    @Override
    protected Path getScriptsDir() {
        return Paths.get("config", "crash_assistant", "scripts", "startup");
    }

    @Override
    protected JexlContext createContext() {
        return super.createBaseContext();
    }

    public static void runStartupSequence(Path appJarPath, Path modJarPath) {
        INSTANCE.runScripts();
    }
}
