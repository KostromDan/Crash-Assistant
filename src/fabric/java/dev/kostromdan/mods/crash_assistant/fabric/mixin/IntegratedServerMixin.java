package dev.kostromdan.mods.crash_assistant.fabric.mixin;

import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;

import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {
    @Inject(method = "init", at = @At("RETURN"), cancellable = false)
    private void handleServerStarting(CallbackInfoReturnable<Boolean> cir) {
        IntegratedServer server = (IntegratedServer) (Object) this;
        server.getCommandManager().getDispatcher().register(CrashAssistantCommands.getCommands());
    }
}
