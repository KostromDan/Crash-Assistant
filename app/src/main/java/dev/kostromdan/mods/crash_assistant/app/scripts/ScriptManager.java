package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.*;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.hs_err_parser.*;
import dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed.Analysis;
import dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed.Logger;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ScriptManager {
    public static final Path SCRIPTS_DIR = Paths.get("config", "crash_assistant", "scripts", "log_analysis");

    public static void runAnalysisScripts() {
        if (!Files.exists(SCRIPTS_DIR) || !Files.isDirectory(SCRIPTS_DIR)) {
            return;
        }

        JexlEngine engine = Permissions.getEngine();
        JexlContext context = new MapContext();
        context.set("Analysis", Analysis.class);
        context.set("Logger", Logger.class);
        context.set("LogsList", LogsList.class);
        context.set("LogType", LogType.class);
        for (LogType type : LogType.values()) {
            context.set(type.name(), type);
        }
        context.set("Pattern", Pattern.class);
        context.set("RegexChecker", RegexChecker.class);
        context.set("Log", Log.class);
        context.set("LogComparator", LogComparator.class);
        context.set("LogReader", LogReader.class);
        context.set("HsErrParser", HsErrParser.class);
        context.set("HsErrParsingResult", HsErrParsingResult.class);
        

        try (Stream<Path> paths = Files.walk(SCRIPTS_DIR)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jexl"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> {
                        CrashAssistantApp.LOGGER.info("Running script: " + path.getFileName());
                        try {
                            String scriptContent = new String(Files.readAllBytes(path));
                            JexlScript script = engine.createScript(scriptContent);
                            script.execute(context);
                        } catch (IOException e) {
                            CrashAssistantApp.LOGGER.error("Failed to read script: " + path, e);
                        } catch (Exception e) {
                            CrashAssistantApp.LOGGER.error("Error executing script: " + path, e);
                        }
                    });
        } catch (IOException e) {
            CrashAssistantApp.LOGGER.error("Failed to walk scripts directory: " + SCRIPTS_DIR, e);
        }
    }
}
