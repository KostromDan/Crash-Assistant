package dev.kostromdan.mods.crash_assistant.core_mod.services;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.core_mod.utils.IModLocatorInjector;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CrashAssistantApp should be launched as soon as possible after game start
 * to be able to help players even with coremod/mixin/hs_err crashes.
 * So we launch it from initialize of ITransformationService, the first point, we can launch it from forge mod.
 */
public class CrashAssistantTransformationService implements ITransformationService {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantTransformationService");


    @Override
    public @NotNull String name() {
        return "crash_assistant";
    }

    @Override
    public void initialize(IEnvironment environment) {
        String launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("unknown");
        PlatformHelp.platform = PlatformHelp.FORGE;

        try {
            Field versionField = FMLLoader.class.getDeclaredField("mcVersion");
            versionField.setAccessible(true);
            PlatformHelp.minecraftVersion = String.valueOf(versionField.get(null));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to read Minecraft version from FMLLoader", e);
        }

        LibrariesJarLocator.setupLoaderJarName(FMLLoader.class);
        JarInJarHelper.launchCrashAssistantApp(launchTarget);
        JarInJarHelper.checkForIncompatibleMods(true);
        JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
    }

    @Override
    public void beginScanning(IEnvironment iEnvironment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices){
        IModLocatorInjector.inject();
    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return new ArrayList<>();
    }
}
