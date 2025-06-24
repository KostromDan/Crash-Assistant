package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsedByAnotherProcess extends KnownCrashReason {
    public UsedByAnotherProcess() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.used_by_another_process"),
                "java\\.nio\\.file\\.FileSystemException: (.+): The process cannot access the file because it is being used by another process"
        );
    }

    @Override
    public boolean matches(Log log) {
        try {
            String logContent = log.getReader().getAllLinesString();
            Matcher matcher = Pattern.compile(this.patterns.get(0)).matcher(logContent);

            if (matcher.find()) {
                String filePath = matcher.group(1);
                this.message = this.message.replace("$CONFIG_FILE$", filePath);
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
