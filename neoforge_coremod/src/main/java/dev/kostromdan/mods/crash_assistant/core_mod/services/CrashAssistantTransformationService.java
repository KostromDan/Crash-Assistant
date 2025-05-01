package dev.kostromdan.mods.crash_assistant.core_mod.services;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * CrashAssistantApp should be launched as soon as possible after game start
 * to be able to help players even with coremod/mixin/hs_err crashes.
 * So we launch it from initialize of ITransformationService, the first point, we can launch it from forge mod.
 */
public class CrashAssistantTransformationService implements ITransformationService {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantTransformationService");

    static {
        JarInJarHelper.checkForMalwareMods(true);
    }

    @Override
    public @NotNull String name() {
        return "AAA_crash_assistant";
    }

    @Override
    public void initialize(IEnvironment environment) {
        String launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("unknown");
        PlatformHelp.platform = PlatformHelp.NEOFORGE;
        PlatformHelp.minecraftVersion = FMLLoader.versionInfo().mcVersion();
        LibrariesJarLocator.setupLoaderJarName("neoforge-" + FMLLoader.versionInfo().neoForgeVersion());
        JarInJarHelper.launchCrashAssistantApp(launchTarget);
        JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public @NotNull List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
