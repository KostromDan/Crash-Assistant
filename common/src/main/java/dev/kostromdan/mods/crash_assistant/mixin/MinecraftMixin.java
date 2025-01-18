package dev.kostromdan.mods.crash_assistant.mixin;

import dev.kostromdan.mods.crash_assistant.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;
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
    /**
     * Minecraft.stop launches only on normal exit or if crash report generated.
     * This way we detect crashes without crash report or hs_err.
     */
    @Inject(method = "stop", at = @At("RETURN"), cancellable = false)
    private void stop(CallbackInfo ci) {
        String normalStopFileName = "normal_stop_pid" + ProcessHandle.current().pid() + ".tmp";
        Path normalStopFilePath = Paths.get("local", "crash_assistant", normalStopFileName);
        try {
            Files.write(normalStopFilePath, Long.toString(System.currentTimeMillis()).getBytes());
        } catch (IOException ignored) {
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"), cancellable = false)
    private void afterInit(CallbackInfo ci) {
        CrashAssistant.playerNickname = Minecraft.getInstance().getUser().getName();
        try {
            Files.write(ModListUtils.USERNAME_FILE, CrashAssistant.playerNickname.getBytes());
        } catch (IOException ignored) {
        }
    }
}