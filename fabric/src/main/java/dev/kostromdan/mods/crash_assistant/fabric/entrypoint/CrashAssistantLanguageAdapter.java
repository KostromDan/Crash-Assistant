package dev.kostromdan.mods.crash_assistant.fabric.entrypoint;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CrashAssistantLanguageAdapter implements LanguageAdapter {
    private static final Logger LOGGER = LogManager.getLogger("CrashAssistantLanguageAdapter");

    /**
     * CrashAssistantApp should be launched as soon as possible after game start
     * to be able to help players even with LanguageAdapter/MixinConfigPlugin/mixin/hs_err crashes.
     * So we launch it from the constructor of the LanguageAdapter,
     * which is the first point we can launch it from in Fabric.
     *
     * <p>This block finds required libraries on the JVM's classpath,
     * creates a dedicated "parent-last" class loader, and runs the setup logic with it.
     * This is done to make libraries like Apache Commons IO available before
     * Fabric's main class loader is fully configured.
     */
    public CrashAssistantLanguageAdapter() {
        if (Boolean.getBoolean("dev.kostromdan.mods.crash_assistant.startedFlag")) return;
        System.setProperty("dev.kostromdan.mods.crash_assistant.startedFlag", "true");

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // Find the required JARs from the system classpath.
            List<URL> requiredJarUrls = findRequiredLibrariesOnClasspath();

            if (requiredJarUrls.isEmpty()) {
                // This is a fatal error, as the setup cannot proceed.
                throw new RuntimeException("Could not find required libraries (commons-io, oshi-core, jna) on the classpath.");
            }

            // Create a new parent-last class loader that includes the JARs we found.
            ParentLastURLClassLoader customLoader = new ParentLastURLClassLoader(requiredJarUrls.toArray(new URL[0]), CrashAssistantLanguageAdapter.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(customLoader);

            // Load and run our setup logic using the new, library-aware class loader.
            Class<?> runnerClass = customLoader.loadClass(SetupRunner.class.getName());
            Runnable runner = (Runnable) runnerClass.getConstructor().newInstance();
            runner.run();

        } catch (Throwable throwable) {
            LOGGER.error("A critical error occurred during Crash Assistant setup:", throwable);
            throw new RuntimeException(throwable);
        } finally {
            // ALWAYS restore the original class loader to prevent side effects.
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static List<URL> findRequiredLibrariesOnClasspath() {
        // Use a Set to automatically handle duplicate entries from the classpath.
        Set<URL> foundUrls = new HashSet<>();
        String classPath = System.getProperty("java.class.path");
        String separator = File.pathSeparator;

        String[] paths = classPath.split(separator);

        // Find all required dependencies from the classpath
        for (String pathStr : paths) {
            if (pathStr.contains("commons-io") || pathStr.contains("oshi-core") || pathStr.contains("jna-")) {
                try {
                    URL url = Paths.get(pathStr).toUri().toURL();
                    foundUrls.add(url);
                } catch (Exception e) {
                    // In a release version, we suppress this warning. If a JAR isn't found, the main check will fail.
                }
            }
        }

        // Reliably find our own mod's JAR file.
        try {
            CodeSource codeSource = CrashAssistantLanguageAdapter.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                URL modJarUrl = codeSource.getLocation();
                foundUrls.add(modJarUrl);
            }
        } catch (Exception e) {
            LOGGER.error("Could not determine location of CrashAssistant JAR", e);
        }

        // Convert the Set back to a List for the URLClassLoader.
        return new ArrayList<>(foundUrls);
    }

    /**
     * A custom ClassLoader that follows a "parent-last" or "child-first" delegation model.
     * It will attempt to load classes and resources from its own URLs before delegating to the parent.
     * This is necessary to bypass restrictions and classpath issues in the parent (Fabric's KnotClassLoader) at early startup.
     */
    private static class ParentLastURLClassLoader extends URLClassLoader {
        public ParentLastURLClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                // First, check if the class has already been loaded
                Class<?> c = findLoadedClass(name);

                if (c == null) {
                    // If not loaded, try to load it from our own URLs first
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        // If our loader can't find it, then delegate directly to the parent.
                        c = getParent().loadClass(name);
                    }
                }

                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            // Search local URLs first.
            Enumeration<URL> localResources = findResources(name);

            // If we found any resources locally, return them without asking the parent.
            // This prevents duplicate resources from being found, which solves the OSHI warning.
            if (localResources.hasMoreElements()) {
                return localResources;
            }

            // If we didn't find the resource locally, delegate to the parent.
            return getParent().getResources(name);
        }
    }

    /**
     * A dedicated inner class to run the setup logic. This isolates the code
     * so it can be loaded by our custom class loader.
     */
    public static class SetupRunner implements Runnable {
        @Override
        public void run() {
            String launchTarget = FabricLoader.getInstance().getEnvironmentType().toString();
            FabricLoader.getInstance().getModContainer("minecraft")
                    .ifPresent(container -> {
                        PlatformHelp.minecraftVersion = container.getMetadata().getVersion().getFriendlyString();
                    });
            if (ClassExistenceChecker.classExists("org.sinytra.connector.loader.ConnectorEarlyLoader")) {
                PlatformHelp.modLoadedWithConnector = true;
                LOGGER.warn("Seems like you using fabric version of Crash Assistant on forge/neoforge with help of Sinytra Connector. " +
                        "It not known to cause issues (except gui won't display on some very early crashes), " +
                        "but not recommended, since native mod version for forge/neoforge exists.");
                if (FabricLoader.getInstance().isModLoaded("neoforge")) {
                    PlatformHelp.platform = PlatformHelp.NEOFORGE;
                } else {
                    PlatformHelp.platform = PlatformHelp.FORGE;
                }
            } else if (FabricLoader.getInstance().isModLoaded("quilt_loader")) {
                PlatformHelp.platform = PlatformHelp.QUILT;
            } else {
                PlatformHelp.platform = PlatformHelp.FABRIC;
            }


            if (PlatformHelp.modLoadedWithConnector) {
                try {
                    if (PlatformHelp.platform == PlatformHelp.NEOFORGE) {
                        Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
                        Object versionInfo = fmlLoaderClass.getMethod("versionInfo").invoke(null);
                        String neoForgeVersion = (String) versionInfo.getClass().getMethod("neoForgeVersion").invoke(versionInfo);
                        LibrariesJarLocator.setupLoaderJarName("neoforge-" + neoForgeVersion);
                    } else if (PlatformHelp.platform == PlatformHelp.FORGE) {
                        Class<?> versionInfoClass = Class.forName("net.minecraftforge.fml.loading.VersionInfo");
                        LibrariesJarLocator.setupLoaderJarName(versionInfoClass);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to setup loader jar name via reflection for Connector environment", e);
                }
            } else {
                LibrariesJarLocator.setupLoaderJarName(FabricLoader.class);
            }
            JarInJarHelper.launchCrashAssistantApp(launchTarget);
        }
    }

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        throw new IllegalStateException();
    }
}
