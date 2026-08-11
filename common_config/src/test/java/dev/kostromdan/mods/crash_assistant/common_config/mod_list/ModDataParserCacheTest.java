package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class ModDataParserCacheTest {
    private static final String JAR_NAME = "cache-replacement-test.jar";

    private ModDataParserCacheTest() {
    }

    public static void main(String[] args) throws Exception {
        Path jarPath = Paths.get("mods", JAR_NAME);
        Path cachePath = Paths.get("local", "crash_assistant", "mod_data_cache_v5",
                JAR_NAME + ".mod_data.json");
        Files.createDirectories(jarPath.getParent());

        try {
            writeFabricJar(jarPath, "1.0", "first");
            Mod first = ModDataParser.parseModData(jarPath);
            assertEquals("1.0", first.getVersion(), "first parsed version");
            assertTrue(first.getModrinthHash() != null, "first parsed hash");

            Files.write(cachePath, new com.google.gson.Gson().toJson(first)
                    .getBytes(StandardCharsets.UTF_8));
            assertTrue(ModDataParser.getModFromCache(jarPath) == null,
                    "legacy cache entry without file identity is invalidated");

            first = ModDataParser.parseModData(jarPath);
            assertEquals("1.0", first.getVersion(), "legacy cache is replaced by a valid entry");
            assertEquals(first.getModrinthHash(), ModDataParser.getModFromCache(jarPath).getModrinthHash(),
                    "unchanged jar reuses cache");

            writeFabricJar(jarPath, "2.0", "second payload is deliberately longer");
            Mod second = ModDataParser.parseModData(jarPath);

            assertEquals("2.0", second.getVersion(), "replacement under the same filename is reparsed");
            assertTrue(!first.getModrinthHash().equals(second.getModrinthHash()),
                    "replacement under the same filename receives fresh hashes");
        } finally {
            Files.deleteIfExists(jarPath);
            Files.deleteIfExists(cachePath);
        }

        System.out.println("Mod-data parser cache tests passed.");
    }

    private static void writeFabricJar(Path jarPath, String version, String payload) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            writeEntry(output, "fabric.mod.json",
                    "{\"schemaVersion\":1,\"id\":\"cache_test\",\"name\":\"Cache Test\",\"version\":\"" +
                            version + "\"}");
            writeEntry(output, "cache-test-payload.txt", payload);
        }
    }

    private static void writeEntry(JarOutputStream output, String name, String value) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
