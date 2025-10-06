package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.core_mod.utils.IModLocatorInjector;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends AbstractJarFileLocator {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantDependencyLocator");

    @Override
    public List<IModFile> scanMods() {
        return IModLocatorInjector.getJarPath()
                .map(p -> {
                    final ModFile modFile = new ModFile(p, this);
                    this.modJars.compute(modFile, (mf, fs) -> this.createFileSystem(mf));

                    return Collections.singletonList((IModFile) modFile);
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
}
