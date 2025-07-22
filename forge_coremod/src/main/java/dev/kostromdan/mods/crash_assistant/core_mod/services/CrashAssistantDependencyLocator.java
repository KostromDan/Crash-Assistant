package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends AbstractJarFileLocator {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantDependencyLocator");

    @Override
    public List<IModFile> scanMods() {
        List<IModFile> mods = new ArrayList<>();
        try {
            Path mod = JarInJarHelper.extractJarInJar("crash_assistant-forge.jar", "crash_assistant-forge.jar");
            Optional<IModFile> modFile = createModWithReflection(mod);
            if (modFile.isPresent()) {
                mods.add(modFile.get());
            }
        } catch (Exception e) {
            LOGGER.error("Error while loading crash_assistant-forge.jar from jar in jar: ", e);
        }
        return mods;
    }
    
    /**
     * Uses reflection to access the private createMod method in AbstractJarFileLocator
     * @param path Path to the mod jar file
     * @return Optional containing the IModFile if successful
     */
    @SuppressWarnings("unchecked")
    private Optional<IModFile> createModWithReflection(Path path) {
        try {
            Method createModMethod = AbstractJarFileLocator.class.getDeclaredMethod("createMod", Path.class);
            createModMethod.setAccessible(true);
            return (Optional<IModFile>) createModMethod.invoke(this, path);
        } catch (Exception e) {
            LOGGER.error("Failed to access createMod method via reflection", e);
            return Optional.empty();
        }
    }

    @Override
    public Stream<Path> scanCandidates() {
        return Stream.empty();
    }

    @Override
    public String name() {
        return "CrashAssistantDependencyLocator";
    }

    @Override
    public void initArguments(Map<String, ?> map) {
    }
}
