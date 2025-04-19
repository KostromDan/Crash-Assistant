package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

public class MacOSIncompatibleShaderDriverIssue extends KnownCrashReason {

    public MacOSIncompatibleShaderDriverIssue() {
        super(
                LogType.HS_ERR,
                LanguageProvider.get("warnings.lib_gl_programmability"),
                "# Problematic frame:\\R"
                        + "# C\\s+\\[libGLProgrammability\\.dylib(?:\\+0x[0-9a-fA-F]+)?\\]\\s+"
                        + "glpLLVMGetFunctionGlobalVariableUse(?:\\+0x[0-9a-fA-F]+)?"
        );
    }

    @Override
    public boolean matches(String logText, Log log) {
        if (!PlatformHelp.isMacOS()) return false;
        return super.matches(logText, log);
    }
}
