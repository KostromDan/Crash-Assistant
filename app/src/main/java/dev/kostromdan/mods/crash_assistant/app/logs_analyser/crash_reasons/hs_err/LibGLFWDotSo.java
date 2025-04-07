package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;

import java.util.HashMap;

public class LibGLFWDotSo extends KnownCrashReason {
    public LibGLFWDotSo() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.libglfw_so", new HashMap<>() {{
                    put("$LINK.GLFW_DOWNLOAD$", LanguageProvider.get("warnings_common.repository"));
                }}),
                "# Problematic frame:\\R# C  \\[libglfw\\.so\\+0x[0-9a-fA-F]+\\]"
        );
    }
}
