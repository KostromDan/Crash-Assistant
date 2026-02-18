package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.Startup;
import dev.kostromdan.mods.crash_assistant.common_config.utils.JavaBinaryLocator;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import org.apache.commons.jexl3.JexlContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

public class StartupScriptManager extends AbstractScriptManager {

    private static final StartupScriptManager INSTANCE = new StartupScriptManager();
    private static final Gson GSON = new Gson();

    @Override
    protected Path getScriptsDir() {
        return Paths.get("config", "crash_assistant", "scripts", "startup");
    }

    @Override
    protected JexlContext createContext() {
        return super.createBaseContext();
    }

    public static void runStartupSequence() {
        // Run scripts
        INSTANCE.runScripts();
        
        // Check for boot warnings
        if (Startup.hasBootWarnings()) {
            displayBootWarnings();
        }
    }

    private static void displayBootWarnings() {
        try {
            String jsonWarnings = GSON.toJson(Startup.getBootWarnings());
            String encodedWarnings = Base64.getEncoder().encodeToString(jsonWarnings.getBytes(StandardCharsets.UTF_8));
            
            LOGGER.info("Launching StartupWarningViewer...");
            
            String javaBin = JavaBinaryLocator.getJavaBinary();
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(javaBin);

            // Construct classpath
            java.util.List<String> classPathEntries = new java.util.ArrayList<>();
            
            // Add current Mod Jar
            classPathEntries.add(LibrariesJarLocator.getOurModJarPath());

            // Add dependencies
            for (Class<?> clazz : ProcessHelper.getNeededForAppClasses()) {
                 try {
                    String libPath = LibrariesJarLocator.getLibraryJarPath(clazz);
                    if (!classPathEntries.contains(libPath)) {
                        classPathEntries.add(libPath);
                    }
                 } catch (Exception ignored) {}
            }
            
            command.add("-cp");
            command.add(String.join(java.io.File.pathSeparator, classPathEntries));
            
            command.add("dev.kostromdan.mods.crash_assistant.app.StartupWarningViewer");
            command.add(encodedWarnings);
            
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            Process process = builder.start();
            process.waitFor();
            
        } catch (Exception e) {
            LOGGER.error("Failed to display boot warnings", e);
        }
    }
}
