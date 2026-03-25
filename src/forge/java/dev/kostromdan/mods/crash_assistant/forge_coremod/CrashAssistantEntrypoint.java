package dev.kostromdan.mods.crash_assistant.forge_coremod;

import java.util.Map;
import java.util.stream.Stream;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraft.launchwrapper.Launch;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public class CrashAssistantEntrypoint implements IFMLLoadingPlugin {

    public CrashAssistantEntrypoint() {
        if (JarInJarHelper.isLwjgl3ifyRelauncher()) return;

        if (Boolean.getBoolean("dev.kostromdan.mods.crash_assistant.startedFlag")) return;
        System.setProperty("dev.kostromdan.mods.crash_assistant.startedFlag", "true");


        String launchTarget = FMLLaunchHandler.side()
            .isClient() ? "client" : "server";
        PlatformHelp.platform = PlatformHelp.FORGE;
        PlatformHelp.platform.setPlatformDataFromOtherPlatform(PlatformHelp.LEGACY_MODDING);
        PlatformHelp.minecraftVersion = Loader.instance().getMCVersionString();

        ArgUtils.setLaunchArgs(((java.util.Map<String, String>) Launch.blackboard.get("launchArgs")).entrySet().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray(String[]::new));

        LibrariesJarLocator.setupLoaderJarName(FMLLaunchHandler.class);
        JarInJarHelper.launchCrashAssistantApp(launchTarget);
        JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
    }

    @Override
    public String[] getLibraryRequestClass() {
        return new String[0];
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
