package dev.kostromdan.mods.crash_assistant.forge_coremod;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
public class CrashAssistantEntrypoint implements IFMLLoadingPlugin {
    public CrashAssistantEntrypoint() {
        if(JarInJarHelper.isCleanroomRelauncher()){
            return;
        }
        String launchTarget = FMLLaunchHandler.side().isClient() ? "client" : "server";
        PlatformHelp.platform = dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp.FORGE;
        PlatformHelp.minecraftVersion = ForgeVersion.mcVersion;

        LibrariesJarLocator.setupLoaderJarName(FMLLaunchHandler.class);
        JarInJarHelper.launchCrashAssistantApp(launchTarget);
        JarInJarHelper.checkForIncompatibleMods(true);
        JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
    }

    public String[] getASMTransformerClass() {
        return new String[] {
            "dev.kostromdan.mods.crash_assistant.forge_coremod.CrashAssistantTransformer"
        };
    }

    public String getModContainerClass() {
        return null;
    }

    @Nullable
    public String getSetupClass() {
        return null;
    }

    public void injectData(Map<String, Object> data) {
    }

    public String getAccessTransformerClass() {
        return null;
    }
}
