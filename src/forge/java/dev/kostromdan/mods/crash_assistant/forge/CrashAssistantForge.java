package dev.kostromdan.mods.crash_assistant.forge;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;

@Mod(
        modid = CrashAssistant.MOD_ID,
        name = "Crash Assistant",
        useMetadata = true
)
public final class CrashAssistantForge {

    private boolean mainMenuOpened = false;
    private int ticksAfterMainMenu = 0;
    private static final int TICKS_TO_WAIT = 1; // Wait for 1 tick after main menu is opened

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            CrashAssistant.init();
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            registerClientCommands();
        }
    }

    @SideOnly(Side.CLIENT)
    private void registerClientCommands() {
        ClientCommandHandler.instance.registerCommand(new CrashAssistantCommands());
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
        CrashAssistantEvents.onGameJoin();
    }
}
