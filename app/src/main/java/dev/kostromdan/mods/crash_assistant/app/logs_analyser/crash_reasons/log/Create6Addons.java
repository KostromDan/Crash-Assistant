package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.log;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.gui.CreateDependencies;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListDiff;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.platform.PlatformHelp;

import java.util.*;
import java.util.List;

public class Create6Addons extends KnownCrashReason {
    public Create6Addons() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.c6a", new HashMap<>() {{
                    put("$LINK.C6A$", LanguageProvider.get("warnings_common.here"));
                }}),
                "Error loading class: com/simibubi/create/(?!foundation/ponder/PonderWorld)(.*) \\(java\\.lang\\.ClassNotFoundException: com\\.simibubi\\.create.*\\)",
                "Error loading class: com/jozufozu/flywheel/.* \\(java\\.lang\\.ClassNotFoundException: com\\.jozufozu\\.flywheel.*\\)",
                "Caused by: org\\.spongepowered\\.asm\\.mixin\\.throwables\\.ClassMetadataNotFoundException: net\\.createmod\\.catnip\\.data\\.Couple"
        );
    }

    @Override
    public boolean matches(String logText, Log log) {
//        if (CrashAssistantApp.gameLaunchedSuccessfully) {
//            return false;
//        }
//        List<Mod> createMods = CreateDependencies.getCurrentCreateMods();
//        if (createMods.isEmpty()) {
//            return false;
//        }
//        ModListDiff diff = ModListDiff.getDiff(true);
//        if (!PlatformHelp.isLinkDefault() && diff.getAddedMods().isEmpty() && diff.getUpdatedMods().isEmpty()) {
//            return false;
//        }
//        if (createMods.size() == 1 &&
//                createMods.get(0).getVersion() != null &&
//                createMods.get(0).getVersion().startsWith("6") &&
//                ModListUtils.getCurrentModList(true).stream()
//                        .anyMatch(mod -> Objects.equals(mod.getModId(), "railways"))) {
//            return true;
//        }
        return super.matches(logText, log);
    }
}