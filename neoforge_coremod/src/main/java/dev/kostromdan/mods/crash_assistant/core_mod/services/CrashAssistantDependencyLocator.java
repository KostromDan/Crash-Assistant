package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends AbstractJarFileModProvider implements IDependencyLocator {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantDependencyLocator");

    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        List<IModFile> mods = new ArrayList<>();
        try {
            mods.add(createMod(JarInJarHelper.getJarInJar("crash_assistant-neoforge.jar")).file());
        } catch (Exception e) {
            LOGGER.error("Error while loading crash_assistant-neoforge.jar from jar in jar: ", e);
        }
        return mods;
    }

    public String name() {
        return "crash_assistant";
    }

    public void initArguments(Map<String, ?> map) {
    }
}
