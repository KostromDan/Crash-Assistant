package dev.kostromdan.mods.crash_assistant.common;

import dev.kostromdan.mods.crash_assistant.common.utils.CurrentGPUDetector;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import net.minecraft.client.Minecraft;

/**
 * This class contains hooks that are called from ASM-transformed classes.
 * These hooks replace the functionality that was previously in mixin callbacks.
 */
public class CrashAssistantHooks {

    public static void afterMinecraftInit() {
        CrashAssistant.playerNickname = Minecraft.getInstance()
            .getUser()
            .getName();
        ProcessSignalIO.postInfo("username", CrashAssistant.playerNickname);
    }

    public static void onMinecraftShutdown() {
        ProcessSignalIO.post("normal_stop");
    }


    public static void onClientLoaded() {
        if (CrashAssistant.clientLoaded) return;
        CrashAssistant.clientLoaded = true;
        CurrentGPUDetector.writeCurrentGPU();

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
