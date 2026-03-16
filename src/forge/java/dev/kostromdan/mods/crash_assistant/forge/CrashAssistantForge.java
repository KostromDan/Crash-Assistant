package dev.kostromdan.mods.crash_assistant.forge;

import com.mojang.brigadier.CommandDispatcher;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod(CrashAssistant.MOD_ID)
public final class CrashAssistantForge {
    public CrashAssistantForge() {
        CrashAssistant.init();
    }

    @Mod.EventBusSubscriber(modid = CrashAssistant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            CrashAssistantEvents.afterMinecraftInit();
        }
    }

    @Mod.EventBusSubscriber(modid = CrashAssistant.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientModEvents {
        private static boolean mainMenuOpened = false;
        private static int ticksAfterMainMenu = 0;
        private static final int TICKS_TO_WAIT = 1; // Wait for 1 tick after main menu is opened

        @SubscribeEvent
        public static void onServerStarting(FMLServerStartingEvent event) {
            CommandDispatcher<?> dispatcher = event.getCommandDispatcher();
            dispatcher.register(CrashAssistantCommands.getCommands());
        }

        @SubscribeEvent
        public static void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
            CrashAssistantEvents.onGameJoin();
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && mainMenuOpened) {
                ticksAfterMainMenu++;
                if (ticksAfterMainMenu >= TICKS_TO_WAIT) {
                    CrashAssistantEvents.onClientLoaded();
                    mainMenuOpened = false;
                    ticksAfterMainMenu = 0;
                }
            }
        }

        @SubscribeEvent
        public static void onGuiOpen(GuiOpenEvent event) {
            Object gui = event.getGui();
            if (gui != null) {
                if (gui instanceof GuiMainMenu) {
                    mainMenuOpened = true;
                    ticksAfterMainMenu = 0;
                }
            }
        }
    }
}
