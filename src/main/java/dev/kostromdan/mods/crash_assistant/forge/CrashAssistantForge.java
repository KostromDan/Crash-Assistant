package dev.kostromdan.mods.crash_assistant.forge;

import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.kostromdan.mods.crash_assistant.Tags;
import dev.kostromdan.mods.crash_assistant.common.CrashAssistant;
import dev.kostromdan.mods.crash_assistant.common.commands.CrashAssistantCommands;
import dev.kostromdan.mods.crash_assistant.common.events.CrashAssistantEvents;
import dev.kostromdan.mods.crash_assistant.forge_coremod.CrashAssistantHooks;

@Mod(
    modid = CrashAssistant.MOD_ID,
    version = Tags.VERSION,
    name = "Crash Assistant",
    acceptedMinecraftVersions = "[1.7.10]")
public final class CrashAssistantForge {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        CrashAssistant.init();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            registerClientCommands();
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            CrashAssistantHooks.afterMinecraftInit();
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

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiErrorScreen) {
            CrashAssistantHooks.onErrorScreenInit();
        } else if (event.gui instanceof GuiMainMenu) {
            CrashAssistantHooks.onClientLoaded();
        }
    }
}
