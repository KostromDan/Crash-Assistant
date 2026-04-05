package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.HashMap;
import java.util.Objects;

public class CtovWithoutLithostitched extends KnownCrashReason {
    public CtovWithoutLithostitched() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.ctov_without_lithostitched", new HashMap<String, String>(){{
                    put("$LINK.LITHOSTITCHED$", "Lithostitched");
                }})
        );
        this.setDontShowAgainKey("warnings.ctov_without_lithostitched");
    }

    @Override
    public boolean matches(Log log) {
        if (CrashAssistantApp.gameLaunchedSuccessfully) return false;
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "ctov"))) {
            return false;
        }
        return ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "lithostitched"));
    }
}
