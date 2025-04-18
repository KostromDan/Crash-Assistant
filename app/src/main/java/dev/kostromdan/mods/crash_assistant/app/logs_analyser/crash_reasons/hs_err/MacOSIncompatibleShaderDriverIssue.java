package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.hs_err;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

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
}
