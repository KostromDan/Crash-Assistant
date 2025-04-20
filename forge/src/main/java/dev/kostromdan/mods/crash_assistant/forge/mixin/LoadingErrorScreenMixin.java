package dev.kostromdan.mods.crash_assistant.forge.mixin;

import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import net.minecraftforge.client.gui.LoadingErrorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LoadingErrorScreen.class)
public class LoadingErrorScreenMixin {
    @Inject(method = "init", at = @At("RETURN"), cancellable = false)
    private void OnErrorScreenInit(CallbackInfo ci) {
        ProcessSignalIO.post("loading_error_fml");
    }
}
