package dev.kostromdan.mods.crash_assistant.common.mixin;

import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.utils.CurrentGPUDetector;
import dev.kostromdan.mods.crash_assistant.common_config.communication.ProcessSignalIO;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Minecraft.class)
public class MinecraftMixin {
    /**
     * Minecraft.stop launches only on normal exit or if crash report generated.
     * This way we detect crashes without crash report or hs_err.
     */
    @Inject(method = "stop", at = @At("RETURN"), cancellable = false)
    private void stop(CallbackInfo ci) {
        ProcessSignalIO.post("normal_stop");
    }

    @Inject(method = "<init>", at = @At("RETURN"), cancellable = false)
    private void afterInit(CallbackInfo ci) {
        CrashAssistant.playerNickname = Minecraft.getInstance().getUser().getName();
        ProcessSignalIO.postInfo("username", CrashAssistant.playerNickname);
        CurrentGPUDetector.writeCurrentGPU();
    }
}