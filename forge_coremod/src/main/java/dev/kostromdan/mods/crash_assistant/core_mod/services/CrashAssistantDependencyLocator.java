package dev.kostromdan.mods.crash_assistant.core_mod.services;

import dev.kostromdan.mods.crash_assistant.core_mod.utils.ModCreatorHelper;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


/**
 * Since forge doesn't load jar in jar mods from coremods, we should do it by ourselves.
 */
public class CrashAssistantDependencyLocator extends AbstractModProvider implements IDependencyLocator {
    public static final Logger LOGGER = LoggerFactory.getLogger("CrashAssistantDependencyLocator");

    static final ModCreatorHelper MOD_CREATOR_HELPER = new ModCreatorHelper();

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        return MOD_CREATOR_HELPER.scanMods(loadedMods);
    }

    public String name() {
        return MOD_CREATOR_HELPER.name();
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {

    }

    @Override
    public void initArguments(Map<String, ?> map) {
        MOD_CREATOR_HELPER.initArguments(map);
    }
}
