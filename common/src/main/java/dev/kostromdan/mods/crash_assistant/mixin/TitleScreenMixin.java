package dev.kostromdan.mods.crash_assistant.mixin;

import dev.kostromdan.mods.crash_assistant.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.mod_list.ModListUtils;
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

        if (CrashAssistantConfig.getBoolean("modpack_modlist.enabled")) {
            if (CrashAssistantConfig.getModpackCreators().isEmpty()) {
                CrashAssistantConfig.addModpackCreator(CrashAssistant.playerNickname);
            }
            if (CrashAssistantConfig.getBoolean("modpack_modlist.auto_update") &&
                    CrashAssistantConfig.getModpackCreators().contains(CrashAssistant.playerNickname)) {
                ModListUtils.saveCurrentModList();
            }
        }
    }
}
