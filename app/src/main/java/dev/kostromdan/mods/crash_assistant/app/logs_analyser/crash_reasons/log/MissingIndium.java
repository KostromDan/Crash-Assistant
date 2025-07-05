package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CreateDependencies;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.app.utils.maven_version_cmp.VersionUtils;
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


public class MissingIndium extends KnownCrashReason {
    public MissingIndium() {
        super(
                new HashSet<LogType>() {{
                    add(LogType.LOG);
                    add(LogType.CRASH_REPORT);
                }},
                LanguageProvider.get("warnings.missing_indium", new HashMap<String, String>() {{
                    put("$LINK.INDIUM$", "Indium");
                }}),
                "Caused by: java\\.lang\\.NullPointerException: No fabric renderer found",
                "java\\.lang\\.NullPointerException: Cannot invoke \"net\\.fabricmc\\.fabric\\.api\\.renderer\\.v1\\.Renderer\\.meshBuilder\\(\\)\" because the return value of \"net\\.fabricmc\\.fabric\\.api\\.renderer\\.v1\\.RendererAccess\\.getRenderer\\(\\)\" is null"



        );
    }

    @Override
    public boolean matches(Log log) {
//        if (!PlatformHelp.isFabricBased()) {
//            return false;
//        }
//        if(ModListUtils.getCurrentModList(true).stream().anyMatch(x->Objects.equals(x.getModId(), "indium"))){
//            return false;
//        }
//        List<Mod> sodiumMods = ModListUtils.getCurrentModList(true).stream().filter(x->Objects.equals(x.getModId(), "sodium")).collect(Collectors.toList());
//        if (sodiumMods.isEmpty()) {
//            return false;
//        }
//        String maxSodiumVersion = sodiumMods.get(0).getVersion();
//        for (Mod sodiumMod : sodiumMods) {
//            if(VersionUtils.isGreaterThanOrEqual(sodiumMod.getVersion(), maxSodiumVersion)) {
//                maxSodiumVersion = sodiumMod.getVersion();
//            }
//        }
//        if(VersionUtils.isGreaterThanOrEqual(maxSodiumVersion, "0.6.0")) {
//            return false;
//        }
        return super.matches(log);

    }
}
