package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.neoforged.fml.classloading.SecureJar;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.locators.JarInJarDependencyLocator;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Since neoforge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends JarInJarDependencyLocator implements IDependencyLocator {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantDependencyLocator");

    /**
     * CrashAssistantApp should be launched as soon as possible after game start
     * to be able to help players even with coremod/mixin/hs_err crashes.
     * So we launch it from the constructor of the IDependencyLocator, the first point, we can launch it from neoforge.
     */
    public CrashAssistantDependencyLocator() {
        try {
            PlatformHelp.platform = PlatformHelp.NEOFORGE;
            PlatformHelp.minecraftVersion = FMLLoader.getCurrent().getVersionInfo().mcVersion();
            LibrariesJarLocator.setupLoaderJarName("neoforge-" + FMLLoader.getCurrent().getVersionInfo().neoForgeVersion());
            JarInJarHelper.launchCrashAssistantApp(FMLLoader.getCurrent().getDist().isClient() ? "client" : "server");
            JarInJarHelper.checkDuplicatedCrashAssistantMod(true);

        } catch (Throwable throwable) {
            LOGGER.error("A critical error occurred during Crash Assistant setup: ", throwable);
        }
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        try {
            SecureJar secureJar = SecureJar.from(Path.of(CrashAssistantDependencyLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            IModFile modFile = IModFile.create(secureJar, JarModsDotTomlModFileReader::manifestParser);

            // Use reflection to access the private loadModFileFrom method
            Method loadModFileFromMethod = JarInJarDependencyLocator.class.getDeclaredMethod(
                "loadModFileFrom", 
                IModFile.class, 
                String.class, 
                IDiscoveryPipeline.class, 
                Map.class
            );
            loadModFileFromMethod.setAccessible(true);

            // Create a raw HashMap since we can't access EmbeddedJarKey
            @SuppressWarnings({"unchecked", "rawtypes"})
            Map createdModFiles = new HashMap<>();

            @SuppressWarnings("unchecked")
            Optional<IModFile> neoForgeMod = (Optional<IModFile>) loadModFileFromMethod.invoke(
                this, 
                modFile, 
                "META-INF/jarjar/crash_assistant-neoforge.jar", 
                pipeline, 
                createdModFiles
            );

            pipeline.addModFile(neoForgeMod.get());
        } catch (Exception e) {
            LOGGER.error("Error while adding crash_assistant-neoforge.jar to pipeline: ", e);
        }
    }
}
