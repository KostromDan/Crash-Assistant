package dev.kostromdan.mods.crash_assistant.forge_coremod;

import java.util.Map;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

@IFMLLoadingPlugin.MCVersion("1.9.4")
public class CrashAssistantEntrypoint implements IFMLLoadingPlugin {

    public CrashAssistantEntrypoint() {
        if (Boolean.getBoolean("dev.kostromdan.mods.crash_assistant.startedFlag")) return;
        System.setProperty("dev.kostromdan.mods.crash_assistant.startedFlag", "true");


        String launchTarget = FMLLaunchHandler.side()
            .isClient() ? "client" : "server";
        PlatformHelp.platform = PlatformHelp.FORGE;
        PlatformHelp.minecraftVersion = Loader.MC_VERSION;

        LibrariesJarLocator.setupLoaderJarName(FMLLaunchHandler.class);
        JarInJarHelper.launchCrashAssistantApp(launchTarget);
        JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
    }

    public String[] getASMTransformerClass() {
        if (FMLLaunchHandler.side().isClient()) {
            return new String[]{"dev.kostromdan.mods.crash_assistant.forge_coremod.CrashAssistantTransformer"};
        } else {
            return new String[0];
        }
    }

    public String getModContainerClass() {
        return null;
    }

    public String getSetupClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {}

    public String getAccessTransformerClass() {
        return null;
    }
}
