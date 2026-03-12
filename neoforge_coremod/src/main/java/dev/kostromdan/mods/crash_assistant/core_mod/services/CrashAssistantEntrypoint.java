package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrashAssistantEntrypoint implements GraphicsBootstrapper {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantEntrypoint");

    /**
     * CrashAssistantApp should be launched as soon as possible after game start
     * to be able to help players even with coremod/mixin/hs_err crashes.
     * So we launch it from the constructor of the GraphicsBootstrapper, the first point, we can launch it from neoforge.
     */
    public CrashAssistantEntrypoint() {
        try {
            PlatformHelp.platform = PlatformHelp.NEOFORGE;
            PlatformHelp.minecraftVersion = FMLLoader.getCurrent().getVersionInfo().mcVersion();
            ArgUtils.setLaunchArgs(FMLLoader.getCurrent().getProgramArgs().getArguments());
            LibrariesJarLocator.setupLoaderJarName("neoforge-" + FMLLoader.getCurrent().getVersionInfo().neoForgeVersion());
            JarInJarHelper.launchCrashAssistantApp(FMLLoader.getCurrent().getDist().isClient() ? "client" : "server");
            JarInJarHelper.checkDuplicatedCrashAssistantMod(true);

        } catch (Throwable throwable) {
            LOGGER.error("A critical error occurred during Crash Assistant setup: ", throwable);
        }
    }

    @Override
    public String name() {
        return "crash_assistant";
    }

    @Override
    public void bootstrap(String[] arguments) {
    }
}
