package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends AbstractJarFileLocator {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantDependencyLocator");

    @Override
    public List<IModFile> scanMods() {
        List<IModFile> mods = new ArrayList<>();
        try {
            ModFile modFile = new ModFile(JarInJarHelper.getJarInJar("crash_assistant-forge.jar"), this, ModFileParser::modsTomlParser);
            this.modJars.compute(modFile, (mf, fs) -> this.createFileSystem(mf));
            mods.add(modFile);
        } catch (Exception e) {
            LOGGER.error("Error while loading crash_assistant-forge.jar from jar in jar: ", e);
        }
        return mods;
    }

    @Override
    public String name() {
        return "crash_assistant";
    }

    @Override
    public void initArguments(Map<String, ?> map) {
    }
}
