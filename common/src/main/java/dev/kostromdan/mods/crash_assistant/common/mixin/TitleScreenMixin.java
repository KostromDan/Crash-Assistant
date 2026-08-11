package dev.kostromdan.mods.crash_assistant.common.mixin;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "tick", at = @At("RETURN"), cancellable = false)
    private void onClientLoaded(CallbackInfo ci) {
        if (CrashAssistant.clientLoaded) return;
        CrashAssistant.clientLoaded = true;

        ModListUtils.scheduleAutomaticUpdate(CrashAssistant.playerNickname);

        ProcessSignalIO.post("successful_launch");
    }
}
