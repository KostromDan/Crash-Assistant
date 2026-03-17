package dev.kostromdan.mods.crash_assistant.fabric.mixin;

import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("RETURN"), cancellable = false)
    private void onGameJoin(Connection connection, ServerPlayer serverPlayer, CallbackInfo ci) {
        CrashAssistantEvents.onGameJoin();
    }
}
