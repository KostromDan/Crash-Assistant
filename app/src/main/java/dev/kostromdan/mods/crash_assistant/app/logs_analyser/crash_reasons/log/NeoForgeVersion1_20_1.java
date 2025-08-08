package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;

import java.util.Objects;

public class NeoForgeVersion1_20_1 extends KnownCrashReason {
    public NeoForgeVersion1_20_1() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.neo_version1_20_1")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (!Objects.equals(PlatformHelp.minecraftVersion, "1.20.1")) return false;
        if (!PlatformHelp.isForgeBased()) return false;
        String loaderJarName = PlatformHelp.loaderJarName;
        if (loaderJarName == null || "UNDEFINED".equals(loaderJarName)) return false;
        if (loaderJarName.endsWith(".jar")) loaderJarName = loaderJarName.substring(0, loaderJarName.length() - 4);
        if (loaderJarName.startsWith("fmlloader-1.20.1-")) loaderJarName = loaderJarName.substring("fmlloader-1.20.1-".length());
        if (loaderJarName.startsWith("loader-")) loaderJarName = loaderJarName.substring("loader-".length());
        if (!loaderJarName.matches("^47\\.\\d+(?:\\.\\d+){0,2}$")) return false;
        return VersionUtils.isLower(loaderJarName, "47.3.0");
    }
}