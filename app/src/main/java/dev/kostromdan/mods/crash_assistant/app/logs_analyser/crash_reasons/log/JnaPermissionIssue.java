package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class JnaPermissionIssue extends KnownCrashReason {
    public JnaPermissionIssue() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.jna_permission_issue"),
                "Caused by: java\\.lang\\.NoClassDefFoundError: Could not initialize class com\\.sun\\.jna\\."
        );
    }
}