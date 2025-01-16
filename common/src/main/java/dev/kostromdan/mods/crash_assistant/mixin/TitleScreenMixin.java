package dev.kostromdan.mods.crash_assistant.mixin;

import dev.kostromdan.mods.crash_assistant.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.utils.ManualCrashThrower;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "tick", at = @At("RETURN"), cancellable = false)
    private void onClientLoaded(CallbackInfo ci) {
        if (CrashAssistant.clientLoaded) return;
        CrashAssistant.clientLoaded = true;

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            if (CrashAssistantConfig.getModpackCreators().isEmpty()) {
                CrashAssistantConfig.addModpackCreator(CrashAssistant.playerNickname);
            }
            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update") &&
                    CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname)) {
                ModListUtils.saveCurrentModList();
            }
        }

        if (Objects.equals(CrashAssistantConfig.get("debug.crash_game_on_event").toString(), "GAME_STARTED")) {
            ManualCrashThrower.crashGame("Debug crash from Crash Assistant mod. 'debug.crash_game_on_event' value of '" + CrashAssistantConfig.getConfigPath() + "' set to 'GAME_STARTED'.");
        }
    }
}
