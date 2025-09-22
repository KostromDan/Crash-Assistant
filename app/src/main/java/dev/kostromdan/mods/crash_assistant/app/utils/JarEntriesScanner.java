package dev.kostromdan.mods.crash_assistant.app.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

/**
 * Utility to scan entries of a jar file and, optionally, its nested jars (jar-in-jar).
 * For each container (top-level jar and each nested jar) it collects a map of
 * entry name -> isDirectory and invokes a provided consumer.
 */
public final class JarEntriesScanner {

    private JarEntriesScanner() {}

    @FunctionalInterface
    public interface EntriesConsumer extends BiConsumer<String, Map<String, Boolean>> {
        // containerName, entries map
    }

    public static void scanJar(Path jarPath, boolean scanNestedJars, EntriesConsumer consumer) {
        String topName = jarPath.getFileName().toString();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Map<String, Boolean> topEntries = new LinkedHashMap<>();
            List<NestedJar> nested = new ArrayList<>();

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                topEntries.put(name, entry.isDirectory());
                if (scanNestedJars && !entry.isDirectory() && name.endsWith(".jar")) {
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        byte[] data = readAllBytes(in);
                        nested.add(new NestedJar(topName + "!/" + name, data));
                    } catch (IOException ignored) {
                    }
                }
            }

            // After collecting all entries for the container, invoke the consumer
            consumer.accept(topName, Collections.unmodifiableMap(topEntries));

            // Process nested jars recursively
            if (scanNestedJars) {
                for (NestedJar nj : nested) {
                    processNested(nj.containerName, nj.data, true, consumer);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void processNested(String containerName, byte[] data, boolean scanNestedJars, EntriesConsumer consumer) {
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(data))) {
            Map<String, Boolean> entriesMap = new LinkedHashMap<>();
            List<NestedJar> nested = new ArrayList<>();

            JarEntry ne;
            while ((ne = jis.getNextJarEntry()) != null) {
                String name = ne.getName();
                boolean isDir = ne.isDirectory();
                entriesMap.put(name, isDir);
                if (scanNestedJars && !isDir && name.endsWith(".jar")) {
                    try {
                        byte[] nestedData = readAllBytes(jis);
                        nested.add(new NestedJar(containerName + "!/" + name, nestedData));
                    } catch (IOException ignored) {
                    }
                }
            }

            consumer.accept(containerName, Collections.unmodifiableMap(entriesMap));

            if (scanNestedJars) {
                for (NestedJar nj : nested) {
                    processNested(nj.containerName, nj.data, true, consumer);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int r;
        while ((r = in.read(tmp)) != -1) buf.write(tmp, 0, r);
        return buf.toByteArray();
    }

    private static class NestedJar {
        final String containerName;
        final byte[] data;
        NestedJar(String containerName, byte[] data) {
            this.containerName = containerName;
            this.data = data;
        }
    }
}