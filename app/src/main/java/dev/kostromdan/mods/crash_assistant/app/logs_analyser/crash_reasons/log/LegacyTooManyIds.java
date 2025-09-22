package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;

public class LegacyTooManyIds extends KnownCrashReason {
    public LegacyTooManyIds() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.legacy_too_many_ids"),
                "java\\.lang\\.RuntimeException: Invalid id 4096 - maximum id range exceeded\\."
        );
    }

    @Override
    public boolean matches(Log log) {
        if (PlatformHelp.platform != PlatformHelp.FORGE) return false;
        if (VersionUtils.isGreater(PlatformHelp.minecraftVersion, "1.12.2")) return false;
        if(PlatformHelp.minecraftVersion.equals("1.12.2")) {
            message = message.replace("$MOD_LINK$", "<a href=\""+ LinksProvider.ROUGHLY_ENOUGH_IDS.getLink() +"\">Roughly Enough IDs</a>");
        }else {
            message = message.replace("$MOD_LINK$", "<a href=\""+ LinksProvider.NOT_ENOUGH_IDS.getLink() +"\">NotEnoughIDs</a>");
        }
        return super.matches(log);
    }
}
