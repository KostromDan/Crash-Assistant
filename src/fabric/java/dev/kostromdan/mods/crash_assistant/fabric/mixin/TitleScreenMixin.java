package dev.kostromdan.mods.crash_assistant.fabric.mixin;

import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GuiMainMenu.class)
public class TitleScreenMixin {
    @Inject(method = "updateScreen", at = @At("RETURN"), cancellable = false)
    private void onClientLoaded(CallbackInfo ci) {
        CrashAssistantEvents.onClientLoaded();
    }
}
