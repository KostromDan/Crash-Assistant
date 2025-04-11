package dev.kostromdan.mods.crash_assistant.forge.mixin;

import net.minecraftforge.client.gui.LoadingErrorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mixin(LoadingErrorScreen.class)
public class LoadingErrorScreenMixin {
    @Inject(method = "init", at = @At("RETURN"), cancellable = false)
    private void OnErrorScreenInit(CallbackInfo ci) {
        String loadingErrorFileName = "loading_error_fml" + ProcessHandle.current().pid() + ".tmp";
        Path loadingErrorFilePath = Paths.get("local", "crash_assistant", loadingErrorFileName);
        try {
            Files.write(loadingErrorFilePath, Long.toString(System.currentTimeMillis()).getBytes());
        } catch (IOException ignored) {
        }
    }
}
