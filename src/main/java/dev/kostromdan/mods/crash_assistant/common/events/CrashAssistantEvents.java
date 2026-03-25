package dev.kostromdan.mods.crash_assistant.common.events;

import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraft.client.Minecraft;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;

public class CrashAssistantEvents {

    public static void onGameJoin() {
        ProcessSignalIO.post("joined_world");
        if (!CrashAssistantConfig.getModpackCreators()
                .contains(CrashAssistant.playerNickname) || CrashAssistantConfig.getBoolean("greeting.shown_greeting")) {
            return;
        }
        CrashAssistantConfig.set("greeting.shown_greeting", true);
        LanguageProvider.updateLang();
    }

    public static void afterMinecraftInit() {
        CrashAssistant.playerNickname = Minecraft.getMinecraft()
                .getSession()
                .getUsername();
        ProcessSignalIO.postInfo("username", CrashAssistant.playerNickname);
    }

    public static void onMinecraftShutdown() {
        ProcessSignalIO.post("normal_stop");
    }

    public static void onErrorScreenInit() {
        ProcessSignalIO.post("loading_error_fml");
    }

    public static void onClientLoaded() {
        if (CrashAssistant.clientLoaded) return;
        CrashAssistant.clientLoaded = true;

        if(PlatformHelp.platform == PlatformHelp.FABRIC){
            CrashAssistant.init();
        }

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            if (CrashAssistantConfig.getModpackCreators()
                    .isEmpty()) {
                CrashAssistantConfig.addModpackCreator(CrashAssistant.playerNickname);
            }
            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update")
                    && CrashAssistantConfig.getModpackCreators()
                    .contains(CrashAssistant.playerNickname)) {
                ModListUtils.saveCurrentModList();
            }
        }

        ProcessSignalIO.post("successful_launch");
    }
}
