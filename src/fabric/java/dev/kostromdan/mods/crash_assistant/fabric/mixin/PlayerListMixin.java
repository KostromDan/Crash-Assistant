package dev.kostromdan.mods.crash_assistant.fabric.mixin;

import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "initializeConnectionToPlayer", at = @At("RETURN"), cancellable = false)
    private void onGameJoin(NetworkManager netManager, EntityPlayerMP playerIn, CallbackInfo ci) {
        CrashAssistantEvents.onGameJoin();
    }
}
