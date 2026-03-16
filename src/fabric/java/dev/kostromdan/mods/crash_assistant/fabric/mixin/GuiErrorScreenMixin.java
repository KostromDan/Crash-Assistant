package dev.kostromdan.mods.crash_assistant.fabric.mixin;

import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.client.gui.GuiErrorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiErrorScreen.class)
public class GuiErrorScreenMixin {
    @Inject(method = "initGui", at = @At("RETURN"), cancellable = false)
    private void onErrorScreenInit(CallbackInfo ci) {
        CrashAssistantEvents.onErrorScreenInit();
    }
}
