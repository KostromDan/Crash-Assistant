package dev.kostromdan.mods.crash_assistant.core_mod.services;

import cpw.mods.jarhandling.SecureJar;
import dev.kostromdan.mods.crash_assistant.loading_utils.Environment;
import net.neoforged.fml.loading.moddiscovery.locators.JarInJarDependencyLocator;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends JarInJarDependencyLocator implements IDependencyLocator {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantDependencyLocator");

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        if (Environment.getCurrentEnvironment() == Environment.SERVER) {
            LOGGER.warn("Crash Assistant is client only mod. Prevented mod loading!");
            return;
        };
        try {
            SecureJar secureJar = SecureJar.from(Path.of(CrashAssistantDependencyLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            IModFile modFile = IModFile.create(secureJar, JarModsDotTomlModFileReader::manifestParser);
            Optional<IModFile> neoForgeMod = loadModFileFrom(modFile, Path.of("META-INF", "jarjar", "crash_assistant-neoforge.jar"), pipeline);
            pipeline.addModFile(neoForgeMod.get());
        } catch (Exception e) {
            LOGGER.error("Error while adding crash_assistant-neoforge.jar to pipeline: ", e);
        }
    }
}
