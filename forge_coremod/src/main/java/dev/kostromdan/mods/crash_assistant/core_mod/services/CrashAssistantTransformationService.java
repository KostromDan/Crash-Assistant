package dev.kostromdan.mods.crash_assistant.core_mod.services;

import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraftforge.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CrashAssistantApp should be launched as soon as possible after game start
 * to be able to help players even with coremod/mixin/hs_err crashes.
 * So we launch it from the constructor of the ITransformationService, the first point, we can launch it from the forge.
 */
public class CrashAssistantTransformationService implements ITransformationService {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantTransformationService");

    private static String earlyLaunchTarget = "unknown";

    public CrashAssistantTransformationService() {
        try {
            reflectivelyExtractLaunchData();
            PlatformHelp.platform = PlatformHelp.FORGE;
            PlatformHelp.minecraftVersion = FMLLoader.versionInfo().mcVersion();
            LibrariesJarLocator.setupLoaderJarName("forge-" + FMLLoader.versionInfo().forgeVersion());
            JarInJarHelper.launchCrashAssistantApp(earlyLaunchTarget);
            JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
        } catch (Throwable throwable) {
            LOGGER.error("A critical error occurred during Crash Assistant setup: ", throwable);
        }
    }

    /**
     * Uses reflection to access ModLauncher's ArgumentHandler and its raw command-line arguments
     * before the environment is fully initialized. This is necessary to get critical information
     * like the launch target and game version at the earliest possible moment.
     */
    private static void reflectivelyExtractLaunchData() {
        try {
            Field argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
            argumentHandlerField.setAccessible(true);
            Object argumentHandler = argumentHandlerField.get(Launcher.INSTANCE);

            Field argsField = ArgumentHandler.class.getDeclaredField("args");
            argsField.setAccessible(true);
            String[] rawArgs = (String[]) argsField.get(argumentHandler);

            ArgUtils.setLaunchArgs(rawArgs);

            if (rawArgs == null) {
                LOGGER.warn("Could not find raw launch arguments via reflection; they were null.");
                return;
            }

            for (int i = 0; i < rawArgs.length - 1; i++) {
                if ("--launchTarget".equals(rawArgs[i])) {
                    earlyLaunchTarget = rawArgs[i + 1];
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            LOGGER.error("Failed to reflectively access ModLauncher arguments. This might happen with a future ModLauncher update.", e);
        }
    }


    @Override
    public @NotNull String name() {
        return "crash_assistant";
    }

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return new ArrayList<>();
    }
}
