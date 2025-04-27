package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface LibrariesJarLocator {
    static String getLibraryJarPath(Class cls) throws JarLocatingException, URISyntaxException {
        Path path = getPathFromClass(cls);

        if (path == null) {
            throw new JarLocatingException("getPathFromClass returned null, class: " + cls.getName());
        }
        if (!Files.exists(path)) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + cls + "',but it does not exist; path: `" + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + cls + "',but it is not regular file; path: `" + path);
        }

        return path.toAbsolutePath().toString();
    }

    static String getLibraryJarPathFromResource(String resource) throws JarLocatingException {
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

    static Path getPathFromClass(Class cls) {
        String resourcePath = cls.getName().replace('.', '/') + ".class";
        return getPathFromResource(resourcePath);
    }

    private static Path getPathFromResource(String resource) {
        ClassLoader cl = LibrariesJarLocator.class.getClassLoader();
        var url = cl.getResource(resource);
        if (url == null)
            return null;
        return getPath(url, resource);
    }

    private static Path getPath(URL url, String resource) {
        var str = url.toString();
        int len = resource.length();
        if ("jar".equalsIgnoreCase(url.getProtocol())) {
            str = url.getFile();
            len += 2;
            str = str.substring(0, str.length() - len);
        } else if ("union".equalsIgnoreCase(url.getProtocol())) {
            str = url.getFile();
            str = "file://" + str.substring(0, str.lastIndexOf(".jar") + 4);
        }
        return Path.of(URI.create(str));
    }

    static void setupLoaderJarName(Class cls) {
        try {
            PlatformHelp.loaderJarName = Paths.get(getLibraryJarPath(cls)).getFileName().toString();
        } catch (URISyntaxException e) {
            JarInJarHelper.LOGGER.error("Error while trying to get loader jar path: ", e);
        }
    }

    static void setupLoaderJarName(String version) {
        PlatformHelp.loaderJarName = version;
    }
}
