package dev.kostromdan.mods.crash_assistant.core_mod.services;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends AbstractJarFileLocator {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantDependencyLocator");

    @Override
    public List<ModFile> scanMods() {
        return getJarPath()
                .map(p -> {
                    final ModFile modFile = new ModFile(p, this);
                    this.modJars.compute(modFile, (mf, fs) -> this.createFileSystem(mf));

                    return Collections.singletonList(modFile);
                })
                .orElse(Collections.emptyList());
    }

    @Override
    public String name() {
        return "CrashAssistantDependencyLocator";
    }

    @Override
    public void initArguments(Map<String, ?> map) {
    }

    public static Optional<URL> getOurJar() {
        final URL url = CrashAssistantDependencyLocator.class.getProtectionDomain().getCodeSource().getLocation();
        final Path p;
        try {
            p = Paths.get(url.toURI());
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }

        if (p.getFileName().toString().toLowerCase().endsWith(".jar")) {
            return Optional.of(url);
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Path> getJarPath() {
        return getOurJar().map(url -> {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
