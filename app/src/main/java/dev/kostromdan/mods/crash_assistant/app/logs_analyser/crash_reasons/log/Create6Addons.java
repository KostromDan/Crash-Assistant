package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp.VersionUtils;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class Create6Addons extends KnownCrashReason {
    public Create6Addons() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.c6a", new HashMap<String, String>() {{
                    put("$LINK.C6A$", LanguageProvider.get("warnings_common.here"));
                }}),
                "(?i)java\\.lang\\.(ClassNotFoundException|NoClassDefFoundError): com[./]simibubi[./]create[./](?!foundation[./]ponder[./]PonderWorld\\b)[^ ]+",
                "(?i)java\\.lang\\.(ClassNotFoundException|NoClassDefFoundError): com[./]jozufozu[./]flywheel",
                "(?i)java\\.lang\\.(ClassNotFoundException|NoClassDefFoundError): dev[./]engine_room[./]flywheel",
                "(?i)java\\.lang\\.(ClassNotFoundException|NoClassDefFoundError): net[./]createmod",
                ".*Caused by: org\\.spongepowered\\.asm\\.mixin\\.throwables\\.ClassMetadataNotFoundException: net\\.createmod\\.catnip\\.data\\.Couple.*"
        );
    }

    @Override
    public boolean matches(Log log) {
        if (VersionUtils.isLower(PlatformHelp.minecraftVersion, "1.20")) {
            return false;
        }
        if (CrashAssistantApp.gameLaunchedSuccessfully) {
            return false;
        }
        List<Mod> createMods = ModListUtils.getCurrentModList(true).stream()
                .filter(mod -> "create".equals(mod.getModId()))
                .collect(Collectors.toList());
        if (createMods.isEmpty()) {
            return false;
        }
        ModListDiff diff = ModListDiff.getDiff(true);
        if (!PlatformHelp.isLinkDefault() && diff.getAddedMods().isEmpty() && diff.getUpdatedMods().isEmpty()) {
            return false;
        }
        if (createMods.size() == 1 && createMods.get(0).getVersion() != null) {
            String creteVersion = createMods.get(0).getVersion();
            Mod railwaysMod = ModListUtils.getCurrentModList(true).stream()
                    .filter(mod -> Objects.equals(mod.getModId(), "railways"))
                    .findFirst().orElse(null);
            if (railwaysMod != null && railwaysMod.getVersion() != null) {
                String railwaysVersion = railwaysMod.getVersion().split("-")[0];
                if (creteVersion.startsWith("6")) {
                    if (VersionUtils.isLower(railwaysVersion, "1.6.10")) return true;
                } else {
                    if (VersionUtils.isGreaterThanOrEqual(railwaysVersion, "1.6.10")) return true;
                }
            }
        }
        return super.matches(log);

    }
}