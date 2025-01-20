package dev.kostromdan.mods.crash_assistant.fabric.pre_launch;

import dev.kostromdan.mods.crash_assistant.loading_utils.JarInJarHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrashAssistantPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LogManager.getLogger("CrashAssistantPreLaunch");

    @Override
    public void onPreLaunch() {
        String launchTarget = FabricLoader.getInstance().getEnvironmentType().toString();
        JarInJarHelper.launchCrashAssistantApp(launchTarget, "FABRIC");
    }
}
