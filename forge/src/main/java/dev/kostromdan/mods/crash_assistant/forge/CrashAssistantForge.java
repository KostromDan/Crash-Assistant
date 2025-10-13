package dev.kostromdan.mods.crash_assistant.forge;

import com.mojang.brigadier.CommandDispatcher;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(CrashAssistant.MOD_ID)
public final class CrashAssistantForge {
    public CrashAssistantForge() {
        CrashAssistant.init();
        // Register modern per-event listeners on client only
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            RegisterClientCommandsEvent.BUS.addListener(event -> {
                CommandDispatcher<CommandSourceStack> disp = event.getDispatcher();
                disp.register(CrashAssistantCommands.getCommands());
            });
            PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> CrashAssistantEvents.onGameJoin());
        });
    }
}
