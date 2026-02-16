package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.scripts.permissions.Permissions;
import dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed.Analysis;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ScriptManager {
    public static final Path SCRIPTS_DIR = Paths.get("config", "crash_assistant", "scripts", "log_analysis");
    private static final Set<String> executedScripts = Collections.synchronizedSet(new HashSet<>());

    public static void runAnalysisScripts() {
        if (!Files.exists(SCRIPTS_DIR) || !Files.isDirectory(SCRIPTS_DIR)) {
            return;
        }

        JexlEngine engine = Permissions.getEngine();
        JexlContext context = createAnalysisContext();

        try (Stream<Path> paths = Files.walk(SCRIPTS_DIR)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jexl"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> runScript(path, engine, context));
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to walk scripts directory: " + SCRIPTS_DIR, e);
        }
    }

    private static JexlContext createAnalysisContext() {
        JexlContext context = new MapContext();

        // 1. Inject whitelist classes into the context.
        Map<String, Class<?>> allowedClasses = Permissions.getClassMap();
        for (Map.Entry<String, Class<?>> entry : allowedClasses.entrySet()) {
            context.set(entry.getKey(), entry.getValue());
        }

        // 2. Inject LogType enum constants.
        for (LogType type : LogType.values()) {
            context.set(type.name(), type);
        }

        return context;
    }

    private static void runScript(Path path, JexlEngine engine, JexlContext context) {
        String scriptName = path.getFileName().toString();

        if (executedScripts.contains(scriptName) && !Analysis.isAlwaysRun(scriptName)) {
            return;
        }

        CrashAssistantApp.LOGGER.info("Running script: " + scriptName);
        Analysis.setCurrentScriptName(scriptName);
        try {
            String scriptContent = new String(Files.readAllBytes(path));
            JexlScript script = engine.createScript(scriptContent);
            script.execute(context);
            executedScripts.add(scriptName);
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to read script: " + path, e);
        } catch (Exception e) {
            CrashAssistantApp.LOGGER.error("Error executing script: " + path, e);
        } finally {
            Analysis.setCurrentScriptName(null);
        }
    }
}
