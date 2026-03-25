package dev.kostromdan.mods.crash_assistant.fabric.entrypoint;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ClassExistenceChecker;
import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CrashAssistantLanguageAdapter implements LanguageAdapter {
    static {
        System.setProperty("log4j2.level", Level.INFO.name());
        System.setProperty("org.apache.logging.log4j.level", Level.INFO.name());
    }
    private static final Logger LOGGER = LogManager.getLogger("CrashAssistantLanguageAdapter");

    public CrashAssistantLanguageAdapter() {
        if (Boolean.getBoolean("dev.kostromdan.mods.crash_assistant.startedFlag")) return;
        System.setProperty("dev.kostromdan.mods.crash_assistant.startedFlag", "true");

        ArgUtils.setLaunchArgs(FabricLoader.getInstance().getLaunchArguments(false));
        FabricLoader.getInstance().getModContainer("minecraft")
                .ifPresent(container -> {
                    PlatformHelp.minecraftVersion = container.getMetadata().getVersion().getFriendlyString();
                });

        try {
            List<String> requiredPaths = ProcessHelper.getPathsToNeededLibs(ProcessHelper.getJnaPredicates());

            if (!requiredPaths.isEmpty()) exposeToKnotClassLoader(requiredPaths);

            SetupRunner runner = new SetupRunner();
            runner.run();

        } catch (Throwable throwable) {
            LOGGER.error("A critical error occurred during Crash Assistant setup:", throwable);
            throw new RuntimeException(throwable);
        }
    }

    private static void exposeToKnotClassLoader(List<String> libPaths) {
        try {
            ClassLoader knotLoader = Thread.currentThread().getContextClassLoader();

            if (!knotLoader.getClass().getSimpleName().equals("KnotClassLoader") &&
                    !knotLoader.getClass().getSimpleName().equals("KnotCompatibilityClassLoader")) {
                LOGGER.warn("Not running in KnotClassLoader, skipping library exposure.");
                return;
            }

            Method addUrlMethod = getMethod(knotLoader.getClass(), "addUrlFwd", URL.class);
            if (addUrlMethod == null) {
                addUrlMethod = getMethod(knotLoader.getClass(), "addURL", URL.class);
            }
            Method addPathMethod = getMethod(knotLoader.getClass(), "addPath", Path.class);

            if (addUrlMethod != null) addUrlMethod.setAccessible(true);
            if (addPathMethod != null) addPathMethod.setAccessible(true);

            Field delegateField = getField(knotLoader.getClass(), "delegate");
            Object delegate = null;
            Method setAllowedPrefixes = null;

            if (delegateField != null) {
                delegateField.setAccessible(true);
                delegate = delegateField.get(knotLoader);
                setAllowedPrefixes = getMethod(delegate.getClass(), "setAllowedPrefixes", Path.class, String[].class);
                if (setAllowedPrefixes != null) {
                    setAllowedPrefixes.setAccessible(true);
                }
            }

            for (String pathStr : libPaths) {
                Path libPath = Paths.get(pathStr).toAbsolutePath().normalize();

                if (addUrlMethod != null) {
                    addUrlMethod.invoke(knotLoader, libPath.toUri().toURL());
                } else if (addPathMethod != null) {
                    addPathMethod.invoke(knotLoader, libPath);
                } else {
                    LOGGER.warn("Could not find a method to add URL/Path to KnotClassLoader!");
                }

                if (delegate != null && setAllowedPrefixes != null) {
                    setAllowedPrefixes.invoke(delegate, libPath, new String[0]);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to expose libraries to Knot class loader via reflection", e);
        }
    }

    private static Field getField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    public static class SetupRunner implements Runnable {
        @Override
        public void run() {
            String launchTarget = FabricLoader.getInstance().getEnvironmentType().toString();
            if (ClassExistenceChecker.classExists("org.sinytra.connector.loader.ConnectorEarlyLoader") ||
                    ClassExistenceChecker.classExists("org.sinytra.connector.ConnectorEarlyLoader")) {
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
