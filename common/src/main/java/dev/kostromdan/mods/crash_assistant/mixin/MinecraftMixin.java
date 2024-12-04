package dev.kostromdan.mods.crash_assistant.mixin;

import dev.kostromdan.mods.crash_assistant.loading_utils.PIDHelper;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    static {
//        ManualCrashThrower.crashGame(); // debug crash on mixin setup.
    }

    /**
     * Minecraft.stop launches only on normal exit. This way we detect crashes without crash report or hs_err.
     */
    @Inject(method = "stop", at = @At("RETURN"), cancellable = false)
    private void stop(CallbackInfo ci) {
        String normalStopFileName = "normal_stop_pid" + PIDHelper.getCurrentProcessID() + ".tmp";
        Path normalStopFilePath = Paths.get("local", "crash_assistant", normalStopFileName);
        try {
            Files.write(normalStopFilePath, Long.toString(System.currentTimeMillis()).getBytes());
        } catch (IOException ignored) {
        }
    }
}