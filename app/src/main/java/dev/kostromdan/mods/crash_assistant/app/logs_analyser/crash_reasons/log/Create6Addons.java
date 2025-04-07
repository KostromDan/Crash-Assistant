package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;

import java.util.HashMap;
import java.util.Objects;


public class Create6Addons extends KnownCrashReason {
    public Create6Addons() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.c6a", new HashMap<>() {{
                    put("$LINK.C6A$", LanguageProvider.get("warnings_common.here"));
                }}),
                "Error loading class: com/simibubi/create/.* \\(java\\.lang\\.ClassNotFoundException: com\\.simibubi\\.create.*\\)",
                "Error loading class: com/jozufozu/flywheel/.* \\(java\\.lang\\.ClassNotFoundException: com\\.jozufozu\\.flywheel.*\\)",
                "Caused by: org\\.spongepowered\\.asm\\.mixin\\.throwables\\.ClassMetadataNotFoundException: net\\.createmod\\.catnip\\.data\\.Couple"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully){
            return false;
        }
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "create"))) {
            return false;
        }
        return super.matches(log);
    }
}
