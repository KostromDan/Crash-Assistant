package dev.kostromdan.mods.crash_assistant.core_mod.services;

import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.locators.JarInJarDependencyLocator;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        try {
            IModFile modFile;
            try {
                // New way
                JarContents jarContents = JarContents.ofPath(Path.of(CrashAssistantDependencyLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
                modFile = IModFile.create(jarContents, JarModsDotTomlModFileReader::manifestParser);
            } catch (NoClassDefFoundError e) {
                // Old way via reflection
                LOGGER.warn("JarContents not found, falling back to SecureJar via reflection for older NeoForge.");
                try {
                    Path path = Path.of(CrashAssistantDependencyLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    Class<?> secureJarClass = Class.forName("net.neoforged.fml.classloading.SecureJar");
                    Method fromMethod = secureJarClass.getMethod("from", Path.class);
                    Object secureJar = fromMethod.invoke(null, path);

                    Class<?> modFileClass = Class.forName("net.neoforged.neoforgespi.locating.IModFile");
                    Class<?> parserInterface = Class.forName("net.neoforged.neoforgespi.locating.IModFile$ModFileInfoParser");

                    Object parserProxy = Proxy.newProxyInstance(
                            CrashAssistantDependencyLocator.class.getClassLoader(),
                            new Class<?>[]{parserInterface},
                            (proxy, method, args) -> JarModsDotTomlModFileReader.class.getMethod("manifestParser", IModFile.class).invoke(null, args)
                    );

                    Method createMethod = modFileClass.getMethod("create", secureJarClass, parserInterface);
                    modFile = (IModFile) createMethod.invoke(null, secureJar, parserProxy);
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException("Failed to create mod file with reflection fallback", ex);
                }
            }

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