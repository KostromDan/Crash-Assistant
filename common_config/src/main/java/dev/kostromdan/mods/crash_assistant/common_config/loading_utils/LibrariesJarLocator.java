package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LibrariesJarLocator {
    public static String getLibraryJarPath(Class cls) throws JarLocatingException, URISyntaxException {
        return getLibraryJarPath(cls, true);
    }

    public static String getLibraryJarPath(Class cls, boolean checkExistence) throws JarLocatingException, URISyntaxException {
        Path path = getPathFromClass(cls);

        if (path == null) {
            throw new JarLocatingException("getPathFromClass returned null, class: " + cls.getName());
        }
        if (checkExistence && !path.toFile().exists()) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + cls + "',but it does not exist; path: `" + path);
        }
        if (checkExistence && !Files.isRegularFile(path)) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + cls + "',but it is not regular file; path: `" + path);
        }

        return path.toAbsolutePath().toString();
    }

    public static String getOurModJarPath() throws JarLocatingException, URISyntaxException {
        try {
            String result = getLibraryJarPath(JarInJarHelper.class);
            if (!PlatformHelp.modLoadedWithConnector) return result;

            // Prevent using mapped by connector jar version.
            Path jarPath = Paths.get(result);
            String fileNameMapped = jarPath.getFileName().toString();
            Path modsFolder = jarPath.getParent().getParent();
            String modNameOriginal = fileNameMapped.split("_mapped_")[0] + ".jar";
            return modsFolder.resolve(modNameOriginal).toAbsolutePath().toString();
        } catch (Exception e) {
            // Quilt with it's stupid QuiltZipPath
            if (PlatformHelp.platform == PlatformHelp.QUILT) {
                try {
                    Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
                    Method getInstanceMethod = fabricLoaderClass.getMethod("getInstance");
                    Object loaderInstance = getInstanceMethod.invoke(null);

                    Method getModContainerMethod = fabricLoaderClass.getMethod("getModContainer", String.class);
                    @SuppressWarnings("unchecked")
                    Optional<Object> modContainerOptional = (Optional<Object>) getModContainerMethod.invoke(loaderInstance, "crash_assistant");

                    if (modContainerOptional.isPresent()) {
                        Object modContainer = modContainerOptional.get();
                        Method getOriginMethod = modContainer.getClass().getMethod("getOrigin");
                        Object modOrigin = getOriginMethod.invoke(modContainer);

                        Method getPathsMethod = modOrigin.getClass().getMethod("getPaths");
                        @SuppressWarnings("unchecked")
                        List<Path> originPaths = (List<Path>) getPathsMethod.invoke(modOrigin);

                        return originPaths.stream()
                                .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                                .findFirst()
                                .map(p -> p.toAbsolutePath().toString())
                                .orElseThrow(() -> new JarLocatingException("Could not find JAR path from Quilt ModOrigin paths"));
                    } else {
                        throw new JarLocatingException("Could not find ModContainer for 'crash_assistant' on Quilt");
                    }
                } catch (Exception reflectionEx) {
                    throw new JarLocatingException("Failed to get mod JAR path on Quilt via reflection: " + reflectionEx.getMessage());
                }
            }
            // If not on Quilt, re-throw the original exception
            throw e;
        }
    }

    public static String getLibraryJarPathFromResource(String resource) throws JarLocatingException {
        Path path = getPathFromResource(resource);

        if (path == null) {
            throw new JarLocatingException("getPathFromClass returned null, resource: " + resource);
        }
        if (!Files.exists(path)) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + resource + "',but it does not exist; path: `" + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + resource + "',but it is not regular file; path: `" + path);
        }
        String absolutePath = path.toAbsolutePath().toString();

        if (!absolutePath.endsWith(".jar")) {
            throw new JarLocatingException("Successfully parsed path of `" + resource + "',but it is not .jar file" + path);
        }
        return absolutePath;
    }

    public static Path getPathFromClass(Class cls) {
        String resourcePath = cls.getName().replace('.', '/') + ".class";
        return getPathFromResource(resourcePath);
    }

    public static void setupLoaderJarName(Class cls) {
        try {
            PlatformHelp.loaderJarName = Paths.get(getLibraryJarPath(cls, false)).getFileName().toString();
        } catch (Exception e) {
            JarInJarHelper.LOGGER.error("Error while trying to get loader jar path: ", e);
        }
    }

    public static void setupLoaderJarName(String version) {
        PlatformHelp.loaderJarName = version;
    }

    private static Path getPathFromResource(String resource) {
        List<ClassLoader> toTry = new ArrayList<>();
        toTry.add(LibrariesJarLocator.class.getClassLoader());
        toTry.add(ClassLoader.getSystemClassLoader());

        URL url = null;
        for (ClassLoader cl : toTry) {
            url = cl.getResource(resource);
            if (url != null) break;
        }
        if (url == null) return null;
        return getPath(url, resource);
    }

    private static Path getPath(URL url, String resource) {
        String str = url.toString();
        int len = resource.length();
        if ("jar".equalsIgnoreCase(url.getProtocol())) {
            str = url.getFile();
            len += 2;
            str = str.substring(0, str.length() - len);
        } else if ("union".equalsIgnoreCase(url.getProtocol())) {
            str = url.getFile();
            str = "file://" + str.substring(0, str.lastIndexOf(".jar") + 4);
        }
        return Paths.get(URI.create(str));
    }
}
