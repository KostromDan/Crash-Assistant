package dev.kostromdan.mods.crash_assistant.forge_coremod;

import java.util.Map;
import java.util.stream.Stream;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

@IFMLLoadingPlugin.MCVersion("1.10.2")
public class CrashAssistantEntrypoint implements IFMLLoadingPlugin {

    public CrashAssistantEntrypoint() {
        if (Boolean.getBoolean("dev.kostromdan.mods.crash_assistant.startedFlag")) return;
        System.setProperty("dev.kostromdan.mods.crash_assistant.startedFlag", "true");


        String launchTarget = FMLLaunchHandler.side()
            .isClient() ? "client" : "server";
        PlatformHelp.platform = PlatformHelp.FORGE;
        PlatformHelp.platform.setPlatformDataFromOtherPlatform(PlatformHelp.LEGACY_MODDING);
        PlatformHelp.minecraftVersion = Loader.MC_VERSION;

        ArgUtils.setLaunchArgs(((java.util.Map<String, String>) Launch.blackboard.get("launchArgs")).entrySet().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray(String[]::new));

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
