package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions.Permissions;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.Logger;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptUtils;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public abstract class AbstractScriptManager {
    private static final class ScriptContext extends MapContext implements JexlContext.ThreadLocal {
    }

    protected static final Set<String> executedScripts = Collections.synchronizedSet(new HashSet<>());

    public static void clearExecutedScripts() {
        executedScripts.clear();
    }

    public static Set<String> getExecutedScripts() {
        return executedScripts;
    }

    /**
     * Returns the directory where scripts are located.
     */
    protected abstract Path getScriptsDir();

    /**
     * Creates and populates the JEXL context for script execution.
     * Subclasses should call super.createInternalContext() or implement their own context creation logic.
     */
    protected abstract JexlContext createContext();

    protected JexlContext createBaseContext() {
        JexlContext context = new ScriptContext();

        // Inject whitelist classes into the context.
        Map<String, Class<?>> allowedClasses = Permissions.getClassMap();
        for (Map.Entry<String, Class<?>> entry : allowedClasses.entrySet()) {
            context.set(entry.getKey(), entry.getValue());
        }

        return context;
    }

    public void runScripts() {
        if (!isEnabled()) {
            return;
        }

        Path scriptsDir = getScriptsDir();
        if (!Files.exists(scriptsDir) || !Files.isDirectory(scriptsDir)) {
            return;
        }

        List<Path> scriptPaths;
        try (Stream<Path> stream = Files.walk(scriptsDir)) {
            scriptPaths = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jexl"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(java.util.stream.Collectors.toList());
        } catch (IOException e) {
            Logger.error("Failed to walk scripts directory: {}", scriptsDir, e);
            return;
        }

        if (scriptPaths.isEmpty()) {
            return;
        }

        JexlEngine engine = Permissions.getEngine();
        JexlContext context = createContext();

        for (Path path : scriptPaths) {
            runScript(path, engine, context);
        }
    }

    protected boolean isEnabled() {
        return CrashAssistantConfig.getBoolean("scripts.enabled");
    }

    protected void runScript(Path path, JexlEngine engine, JexlContext context) {
        Path scriptsDir = getScriptsDir();
        String relativizedPath = scriptsDir.relativize(path).toString().replace('\\', '/');
        String scriptName = scriptsDir.getFileName().toString() + "/" + relativizedPath;

        if (executedScripts.contains(scriptName) && !ScriptUtils.isAlwaysRun(scriptName)) {
            return;
        }

        Logger.info("Running script: {}", scriptName);
        ScriptUtils.setCurrentScriptName(scriptName);
        try {
            // Read script content
            String scriptContent;
            try {
                scriptContent = new String(Files.readAllBytes(path));
            } catch (IOException e) {
                Logger.error("Failed to read script: {}", path, e);
                return;
            }

            // Create and execute script
            try {
                JexlScript script = engine.createScript(scriptContent);
                script.execute(context);
                executedScripts.add(scriptName);
            } catch (Exception e) {
                Logger.error("Error executing script: {}", path, e);
            }
        } finally {
            ScriptUtils.setCurrentScriptName(null);
        }
    }
}
