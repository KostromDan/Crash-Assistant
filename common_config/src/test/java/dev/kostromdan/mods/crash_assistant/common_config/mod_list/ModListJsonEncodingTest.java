package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class ModListJsonEncodingTest {
    private static final byte[] UTF_8_BOM = new byte[]{
            (byte) 0xef, (byte) 0xbb, (byte) 0xbf
    };
    private static final String CHINESE_JAR = "\u4e2d\u6587\u6a21\u7ec4.jar";
    private static final String NON_BMP_JAR = "non-bmp-\ud842\udfb7-\ud83d\ude80.jar";
    private static final String LEGACY_DEFAULT_CHARSET_JAR =
            "\u0441\u0442\u0430\u0440\u044b\u0439-\u043c\u043e\u0434.jar";
    private static final String LEGACY_NATIVE_CHARSET_JAR =
            "\u65e5\u672c\u8a9e-\u30e2\u30c3\u30c9.jar";

    private ModListJsonEncodingTest() {
    }

    public static void main(String[] args) throws Exception {
        assertEquals(Charset.forName("windows-1251"), Charset.defaultCharset(),
                "test JVM must exercise a non-UTF-8 legacy default charset");
        testWriteUsesUtf8BomAndRoundTripsUnicodeNames();
        testReadAcceptsLegacyUtf8WithoutBom();
        testReadAcceptsLegacyDefaultCharset();
        testReadAcceptsLegacyNativeCharset();
        System.out.println("modlist.json encoding tests passed.");
    }

    private static void testWriteUsesUtf8BomAndRoundTripsUnicodeNames() throws Exception {
        Path root = Files.createTempDirectory("modlist-json-utf8-bom");
        try {
            Path target = root.resolve("config/crash_assistant/modlist.json");
            Files.createDirectories(target.getParent());
            LinkedHashSet<Mod> expected = mods(CHINESE_JAR, NON_BMP_JAR);

            ModListUtils.writeModList(target, expected);

            byte[] bytes = Files.readAllBytes(target);
            assertStartsWithUtf8Bom(bytes, "new modlist.json");
            decodeStrictUtf8(bytes, UTF_8_BOM.length,
                    "modlist.json payload after the BOM must be valid UTF-8");
            assertJarNames(expected, ModListUtils.readModList(target),
                    "UTF-8 BOM write/read round-trip");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testReadAcceptsLegacyUtf8WithoutBom() throws Exception {
        Path root = Files.createTempDirectory("modlist-json-legacy-utf8");
        try {
            Path target = root.resolve("modlist.json");
            LinkedHashSet<Mod> expected = mods(CHINESE_JAR, NON_BMP_JAR);
            byte[] bytes = Mod.GSON.toJson(expected, Mod.TYPE).getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
            assertFalse(startsWithUtf8Bom(bytes), "legacy UTF-8 fixture must not contain a BOM");

            assertJarNames(expected, ModListUtils.readModList(target),
                    "legacy UTF-8 without BOM");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testReadAcceptsLegacyDefaultCharset() throws Exception {
        Path root = Files.createTempDirectory("modlist-json-legacy-default");
        try {
            Path target = root.resolve("modlist.json");
            LinkedHashSet<Mod> expected = mods(LEGACY_DEFAULT_CHARSET_JAR);
            byte[] bytes = Mod.GSON.toJson(expected, Mod.TYPE)
                    .getBytes(Charset.defaultCharset());
            Files.write(target, bytes);
            assertFalse(isStrictUtf8(bytes),
                    "legacy windows-1251 fixture must force the default-charset fallback");

            assertJarNames(expected, ModListUtils.readModList(target),
                    "legacy default-charset file");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testReadAcceptsLegacyNativeCharset() throws Exception {
        Path root = Files.createTempDirectory("modlist-json-legacy-native");
        String previousNativeEncoding = System.getProperty("native.encoding");
        try {
            Charset nativeCharset = Charset.forName("Shift_JIS");
            System.setProperty("native.encoding", nativeCharset.name());
            Path target = root.resolve("modlist.json");
            LinkedHashSet<Mod> expected = mods(LEGACY_NATIVE_CHARSET_JAR);
            byte[] bytes = Mod.GSON.toJson(expected, Mod.TYPE).getBytes(nativeCharset);
            Files.write(target, bytes);
            assertFalse(isStrictUtf8(bytes),
                    "legacy native-charset fixture must force the native.encoding fallback");

            assertJarNames(expected, ModListUtils.readModList(target),
                    "legacy native.encoding file");
        } finally {
            if (previousNativeEncoding == null) {
                System.clearProperty("native.encoding");
            } else {
                System.setProperty("native.encoding", previousNativeEncoding);
            }
            deleteRecursively(root);
        }
    }

    private static LinkedHashSet<Mod> mods(String... jarNames) {
        LinkedHashSet<Mod> mods = new LinkedHashSet<Mod>();
        for (String jarName : jarNames) {
            mods.add(new Mod(jarName));
        }
        return mods;
    }

    private static void assertJarNames(LinkedHashSet<Mod> expected,
                                       LinkedHashSet<Mod> actual,
                                       String message) {
        List<String> expectedNames = jarNames(expected);
        List<String> actualNames = jarNames(actual);
        assertEquals(expectedNames, actualNames, message);
    }

    private static List<String> jarNames(LinkedHashSet<Mod> mods) {
        List<String> names = new ArrayList<String>();
        for (Mod mod : mods) {
            names.add(mod.getJarName());
        }
        return names;
    }

    private static void assertStartsWithUtf8Bom(byte[] bytes, String message) {
        assertTrue(startsWithUtf8Bom(bytes), message + " must start with EF BB BF");
    }

    private static boolean startsWithUtf8Bom(byte[] bytes) {
        return bytes.length >= UTF_8_BOM.length
                && bytes[0] == UTF_8_BOM[0]
                && bytes[1] == UTF_8_BOM[1]
                && bytes[2] == UTF_8_BOM[2];
    }

    private static String decodeStrictUtf8(byte[] bytes, int offset, String message) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError(message, e);
        }
    }

    private static boolean isStrictUtf8(byte[] bytes) {
        try {
            decodeStrictUtf8(bytes, 0, "fixture");
            return true;
        } catch (AssertionError expected) {
            return false;
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            List<Path> ordered = new ArrayList<Path>();
            paths.forEach(ordered::add);
            ordered.sort(Comparator.reverseOrder());
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }
}
