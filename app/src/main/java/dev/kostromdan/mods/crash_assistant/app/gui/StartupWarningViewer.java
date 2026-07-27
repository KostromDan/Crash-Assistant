package dev.kostromdan.mods.crash_assistant.app.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReasonMessage;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log.ScriptedAnalysis;
import dev.kostromdan.mods.crash_assistant.app.utils.SwingEDT;
import dev.kostromdan.mods.crash_assistant.app.utils.ThemeUtils;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class StartupWarningViewer {
    private static final Logger LOGGER = LogManager.getLogger("StartupWarningViewer");
    public static boolean isStartupWarning = false;

    public static void main(String[] args) {
        ThemeUtils.ensureThemesApplied();

        isStartupWarning = true;

        if (args.length < 1) {
            LOGGER.error("No arguments provided to StartupWarningViewer");
            return;
        }

        try {
            String encodedWarnings = args[0];
            String jsonWarnings = new String(Base64.getDecoder().decode(encodedWarnings), StandardCharsets.UTF_8);
            
            Type listType = new TypeToken<List<ScriptWarning>>(){}.getType();
            List<ScriptWarning> warnings = new Gson().fromJson(jsonWarnings, listType);

            if (warnings != null && !warnings.isEmpty()) {
                for (ScriptWarning warning : warnings) {
                    KnownCrashReason reason = new ScriptedAnalysis(LogType.CRASH_ASSISTANT, warning);
                    KnownCrashReasonMessage.addCrashReasonMessage(new KnownCrashReasonMessage(null, reason));
                }
                
                CrashAssistantGUI.showKnownCrashReasonsWarnings();
            }

        } catch (Exception e) {
            LOGGER.error("Failed to display startup warnings", e);
            SwingEDT.runAndWait(() ->
                    JOptionPane.showMessageDialog(
                            null,
                            "Error displaying startup warnings: " + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    )
            );
        }
        System.exit(0);
    }
}
