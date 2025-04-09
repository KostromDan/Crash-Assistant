package dev.kostromdan.mods.crash_assistant.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import dev.kostromdan.mods.crash_assistant.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.events.CrashAssistantEvents;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(CrashAssistant.MOD_ID)
public final class CrashAssistantNeoForge {
    public CrashAssistantNeoForge() {
        CrashAssistant.init();
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientModEvents.class);
        }

    }

    public static class ClientModEvents {
        @SubscribeEvent
        public static void RegisterClientCommandsEvent(RegisterClientCommandsEvent event) {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(CrashAssistantCommands.getCommands());
        }

        @SubscribeEvent
        public static void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
            CrashAssistantEvents.onGameJoin();
        }
    }
}