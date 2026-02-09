package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

import java.util.Objects;

public class WaterMediaVLCMissing extends KnownCrashReason {
    public WaterMediaVLCMissing() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.watermedia_vlc_missing")
        );
    }

    @Override
    public boolean matches(Log log) {
        if (ModListUtils.getCurrentModList(true).stream().noneMatch(mod -> Objects.equals(mod.getModId(), "watermedia")))
            return false;
        String logText = log.getReader().getAllLinesString();
        return logText.contains("doesn't contains VLC binaries for your OS and ARCH, you had to download it manually from 'https://www.videolan.org/vlc/'");

    }
}
