package dev.kostromdan.mods.crash_assistant.fabric.mixin;

import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.server.management.ServerConfigurationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerConfigurationManager.class)
public class ServerConfigurationManagerMixin {
    @Inject(method = "initializeConnectionToPlayer", at = @At("RETURN"), cancellable = false)
    private void onGameJoin(INetworkManager par2EntityPlayerMP, EntityPlayerMP par2, CallbackInfo ci) {
        CrashAssistantEvents.onGameJoin();
    }
}
