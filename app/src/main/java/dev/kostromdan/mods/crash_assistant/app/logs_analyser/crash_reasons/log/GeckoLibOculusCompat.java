package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GeckoLibOculusCompat extends KnownCrashReason {
    public GeckoLibOculusCompat() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.geckolib_oculus_compat")
        );
        this.setDontShowAgainKey("warnings.geckolib_oculus_compat");
    }

    @Override
    public boolean matches(Log log) {
        if (VersionUtils.isLower(PlatformHelp.minecraftVersion, "1.20.1")) return false;
        List<Mod> detectedMods = ModListUtils.getCurrentModList(true).stream().filter(mod -> Objects.equals(mod.getModId(), "geckoanimfix")).collect(Collectors.toList());
        if (detectedMods.isEmpty()) return false;
        message = message.replace("$JAR_NAME$", "<strong style='color: red;'>" + detectedMods.get(0).getJarName() + "</strong>");
        return true;
    }
}
