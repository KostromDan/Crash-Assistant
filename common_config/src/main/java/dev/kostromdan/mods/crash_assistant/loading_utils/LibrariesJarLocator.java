package dev.kostromdan.mods.crash_assistant.loading_utils;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public interface LibrariesJarLocator {
    static String getLibraryJarPath(Class cls) throws JarLocatingException, URISyntaxException {
        String pathString = cls.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        int jarIndex = pathString.lastIndexOf(".jar");
        if (jarIndex == -1) {
            throw new JarLocatingException("Not found '.jar' in IRI.getPath() of `" + cls + "'; path: " + pathString);
        }

        pathString = pathString.substring(0, jarIndex + 4);

        Path path;
        try {
            path = Paths.get(pathString);
        } catch (Exception e) {
            try {
                if (pathString.startsWith("/")) {
                    pathString = pathString.substring(1);
                }
                path = Paths.get(pathString);
            } catch (Exception e2) {
                throw new JarLocatingException("Failed converting pathString got from `" + cls + "' to Paths.get(pathString); pathString: `" + pathString + "`");
            }
        }

        if (!Files.exists(path)) {
            throw new JarLocatingException("Successfully parsed '.jar' path of `" + cls + "',but it does not exist; path: `" + path + "`; pathString: `" + pathString + "`");
        }

        return path.toAbsolutePath().toString();
    }
}
