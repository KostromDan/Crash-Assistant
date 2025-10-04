package dev.kostromdan.mods.crash_assistant.forge_coremod;

import net.minecraft.client.Minecraft;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;

/**
 * This class contains hooks that are called from ASM-transformed classes.
 * These hooks replace the functionality that was previously in mixin callbacks.
 */
public class CrashAssistantHooks {

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
