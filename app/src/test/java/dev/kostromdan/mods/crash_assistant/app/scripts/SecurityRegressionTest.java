package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions.Permissions;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.Startup;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.MapContext;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class SecurityRegressionTest {
    private static final String VERSION_GATE_SCRIPT =
            "/jexl/fixtures/problematic-mods-version-gate.jexl";
    private static final String MODPACK_RULES_SCRIPT =
            "/jexl/fixtures/modpack-problematic-mods.jexl";
    private static final String[] PAGE_EXAMPLES = {
            "/jexl/pages/log-analysis-example-1.jexl",
            "/jexl/pages/log-analysis-example-2.jexl",
            "/jexl/pages/log-analysis-example-3.jexl",
            "/jexl/pages/log-analysis-example-4.jexl",
            "/jexl/pages/startup-example-1.jexl",
            "/jexl/pages/startup-example-2.jexl",
            "/jexl/pages/startup-example-3.jexl",
            "/jexl/pages/startup-example-4.jexl"
    };

    private static final class TestContext extends MapContext implements JexlContext.ThreadLocal {
    }

    private static JexlEngine engine;
    private static JexlContext context;

    private SecurityRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        setupJexl();
        testProductionPermissionsAndLanguageBasics();
        testDocumentedApi();
        JexlDocumentationCompatibilityTest.run(engine, context);
        testPrimitiveStreamVarargsCompatibility();
        testStartupScriptFixturesWithSyntheticMods();
        testBundledExamplesCompile();
        testFileAndNetworkAccess();
        System.out.println("Security regression tests passed.");
    }

    public static final class ForeignMap extends HashMap<String, String> {
    }

    private static void setupJexl() {
        engine = Permissions.getEngine();
        context = new TestContext();
        for (Map.Entry<String, Class<?>> entry : Permissions.getClassMap().entrySet()) {
            context.set(entry.getKey(), entry.getValue());
        }
        context.set("largeEnumClass", LargeEnum.class);
        ForeignMap foreignMap = new ForeignMap();
        foreignMap.put("key", "value");
        context.set("foreignMap", foreignMap);
        context.set("booleans", new boolean[]{true, false});
        context.set("bytes", new byte[]{1, 2});
        context.set("shorts", new short[]{3, 4});
        context.set("ints", new int[]{5, 6});
        context.set("longs", new long[]{7L, 8L});
        context.set("floats", new float[]{1.5f, 2.5f});
        context.set("doubles", new double[]{3.5d, 4.5d});
        context.set("chars", new char[]{'x', 'y'});
        context.set("syncSet", java.util.Collections.synchronizedSet(new HashSet<String>()));
        context.set("arraysList", java.util.Arrays.asList("a", "b"));
        context.set("singletonMap", java.util.Collections.singletonMap("key", "value"));
    }

    private static void testProductionPermissionsAndLanguageBasics() {
        assertEquals("b", execute("'a:b'.split(':')[1]"));
        assertEquals(2, execute("arraysList.size()"));
        assertEquals(2, execute("Arrays.asList('a', 'b').size()"));
        assertEquals("x", execute("Collections.singletonList('x').get(0)"));
        assertEquals(2, execute("Collections.unmodifiableList(Arrays.asList('a', 'b')).size()"));
        assertEquals(Boolean.TRUE, execute("Collectors.toList() != null"));
        assertEquals(2L, execute("Stream.of('a', 'b').count()"));
        assertEquals(0L, execute("syncSet.stream().count()"));
        assertEquals("key", execute("singletonMap.keySet().iterator().next()"));

        assertEquals(Boolean.TRUE, execute("booleans[0] && !booleans[1]"));
        assertEquals(3, execute("bytes[0] + bytes[1]"));
        assertEquals(7, execute("shorts[0] + shorts[1]"));
        assertEquals(11, execute("ints[0] + ints[1]"));
        assertEquals(15L, execute("longs[0] + longs[1]"));
        assertEquals(4.0d, execute("floats[0] + floats[1]"));
        assertEquals(8.0d, execute("doubles[0] + doubles[1]"));
        assertEquals(241, execute("chars[0] + chars[1]"));
        assertEquals(2, execute("size(ints)"));
        assertEquals(9, execute("ints[0] = 9; ints[0]"));
        assertEquals(15, execute("var total = 0; for (var value : ints) { total += value; } total"));
        assertEquals(Boolean.TRUE, execute(
                "var n = null; n == null && 1 < 2 && 2.0 >= 2 && (3 * 4) == 12"
        ));
    }

    private enum LargeEnum {
        VALUE_00, VALUE_01, VALUE_02, VALUE_03, VALUE_04, VALUE_05, VALUE_06, VALUE_07,
        VALUE_08, VALUE_09, VALUE_10, VALUE_11, VALUE_12, VALUE_13, VALUE_14, VALUE_15,
        VALUE_16, VALUE_17, VALUE_18, VALUE_19, VALUE_20, VALUE_21, VALUE_22, VALUE_23,
        VALUE_24, VALUE_25, VALUE_26, VALUE_27, VALUE_28, VALUE_29, VALUE_30, VALUE_31,
        VALUE_32, VALUE_33, VALUE_34, VALUE_35, VALUE_36, VALUE_37, VALUE_38, VALUE_39,
        VALUE_40, VALUE_41, VALUE_42, VALUE_43, VALUE_44, VALUE_45, VALUE_46, VALUE_47,
        VALUE_48, VALUE_49, VALUE_50, VALUE_51, VALUE_52, VALUE_53, VALUE_54, VALUE_55,
        VALUE_56, VALUE_57, VALUE_58, VALUE_59, VALUE_60, VALUE_61, VALUE_62, VALUE_63,
        VALUE_64
    }

    private static void testDocumentedApi() {
        String oldLoaderJarName = PlatformHelp.loaderJarName;
        String oldMinecraftJvmArgs = Boot.MINECRAFT_JVM_ARGS;
        try {
            PlatformHelp.loaderJarName = "loader-test";
            Boot.MINECRAFT_JVM_ARGS = "jvm-args-test";

            assertEquals("loader-test", execute("PlatformHelp.loaderJarName"));
            assertEquals("jvm-args-test", execute("Boot.MINECRAFT_JVM_ARGS"));
            assertEquals(PlatformHelp.isMacOS(), execute("PlatformHelp.isMacOS()"));
            assertEquals(Boolean.TRUE, execute("ClassExistenceChecker.classExists('java.lang.String')"));
            assertEquals(Boolean.TRUE, execute("Objects.equals('same', 'same')"));
            assertTrue(execute("new('java.lang.Object')") != null, "Object must remain allowed");

            ScriptWarning warning = new ScriptWarning("message");
            context.set("warning", warning);
            assertEquals("arbitrary.key", execute(
                    "warning.withDontShowAgain('arbitrary.key').dontShowAgainKey"
            ));

            Object mapped = execute(
                    "var xs = new('java.util.ArrayList'); " +
                            "xs.add('a'); xs.add('bb'); " +
                            "xs.stream().toMap(x -> x, x -> x.length())"
            );
            assertTrue(mapped instanceof Map, "Stream.toMap must return a map");
            assertEquals(2, ((Map<?, ?>) mapped).size());

            assertEquals("global-value", execute(
                    "Analysis.setGlobal('security-regression-key', 'global-value'); " +
                            "Analysis.getGlobal('security-regression-key')"
            ));
        } finally {
            PlatformHelp.loaderJarName = oldLoaderJarName;
            Boot.MINECRAFT_JVM_ARGS = oldMinecraftJvmArgs;
        }
    }

    private static void testFileAndNetworkAccess() throws Exception {
        context.set("testLog", new Log(LogType.LOG, "test", Paths.get("not-readable-by-script.log")));

        assertUnavailable("testLog.getPath()");
        assertUnavailable("testLog.getFile()");
        assertUnavailable("CrashAssistantConfig.getConfigPath()");
        assertUnavailable("ModListUtils.MODS_FOLDER");
        assertUnavailable("LanguageProvider.OPTIONS_PATH");
        assertUnavailable("new('dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod', 'fake.jar')");
        assertUnavailable("new('java.io.File', 'forbidden')");
        assertUnavailable("new('java.io.FileInputStream', 'forbidden')");
        assertUnavailable("new('java.io.FileOutputStream', 'forbidden')");
        assertUnavailable("new('java.io.FileReader', 'forbidden')");
        assertUnavailable("new('java.io.FileWriter', 'forbidden')");
        assertUnavailable("new('java.io.RandomAccessFile', 'forbidden', 'rw')");
        assertUnavailable("new('java.io.PrintWriter', 'forbidden')");
        assertUnavailable("new('java.util.Formatter', 'forbidden')");
        assertUnavailable("Stream.of(1).map(x -> new('java.io.File', 'forbidden')).findFirst()");
        assertUnavailable("#pragma jexl.import java.io\nnew('File', 'forbidden')");
        assertUnavailable("new('java.net.URL', 'https://example.invalid')");
        assertUnavailable("new('java.net.URI', 'https://example.invalid')");
        assertUnavailable("new('java.net.Socket')");
        assertUnavailable("new('java.net.ServerSocket')");
        assertUnavailable("new('java.lang.ProcessBuilder', 'forbidden-command')");
        assertUnavailable("new('java.util.concurrent.ForkJoinPool')");
        assertUnavailable("Stream.of(1).parallel().map(x -> new('java.net.URL', 'https://example.invalid')).findFirst()");
        assertUnavailable("Integer.TYPE.getClassLoader()");
        assertUnavailable("Integer.TYPE.getDeclaredMethods()");
        assertUnavailable("''.getClass().forName('java.io.File')");
        assertUnavailable("''.getClass().getProtectionDomain()");
        assertUnavailable("PlatformHelp.FORGE.getClass().getClassLoader()");
        assertUnavailable("foreignMap.size()");
        assertUnavailable("foreignMap.entrySet()");
    }

    private static void testPrimitiveStreamVarargsCompatibility() {
        assertEquals(1, execute("IntStream.of(1).sum()"));
        assertEquals(1L, execute("LongStream.of(1).sum()"));
        assertEquals(1.0d, execute("DoubleStream.of(1).sum()"));
        assertPrimitiveVarargsOutcome("IntStream.of(1, 2).sum()", 3);
        assertPrimitiveVarargsOutcome("LongStream.of(1, 2).sum()", 3L);
        assertPrimitiveVarargsOutcome("DoubleStream.of(1, 2).sum()", 3.0d);
    }

    private static void testStartupScriptFixturesWithSyntheticMods() throws Exception {
        Field cacheField = ModListUtils.class.getDeclaredField("cachedModList");
        cacheField.setAccessible(true);
        Object oldCache = cacheField.get(null);
        String oldModpackName = CrashAssistantConfig.get("text.modpack_name");
        try {
            LinkedHashSet<Mod> syntheticMods = new LinkedHashSet<>();
            syntheticMods.add(testMod("aiimprovements", "1.0.0"));
            syntheticMods.add(testMod("jei", "4.15.0"));
            syntheticMods.add(testMod("jei", "4.16.1.1014"));
            syntheticMods.add(testMod("essential-container", "1.0.0"));
            syntheticMods.add(testMod("e4mc", "1.0.0"));
            syntheticMods.add(testMod("optifine", "1.0.0"));
            syntheticMods.add(testMod("unrelated", "1.0.0"));
            cacheField.set(null, syntheticMods);
            CrashAssistantConfig.set("text.modpack_name", "Better MC 3 - audit");
            CrashAssistantLocalConfig.clearAll();

            int beforeFirst = Startup.getCrashWarnings().size();
            executeResource(VERSION_GATE_SCRIPT);
            List<ScriptWarning> afterFirst = Startup.getCrashWarnings();
            assertEquals(beforeFirst + 2, afterFirst.size());
            assertContains(afterFirst.get(beforeFirst).getMessage(), "AI Improvements");
            assertContains(afterFirst.get(beforeFirst + 1).getMessage(), "JEI 1.12");

            int beforeSecond = afterFirst.size();
            executeResource(MODPACK_RULES_SCRIPT);
            List<ScriptWarning> afterSecond = Startup.getCrashWarnings();
            assertEquals(beforeSecond + 3, afterSecond.size());
            assertEquals("problematic_mod_bypass_essential-container",
                    afterSecond.get(beforeSecond).getDontShowAgainKey());
            assertEquals("problematic_mod_bypass_e4mc",
                    afterSecond.get(beforeSecond + 1).getDontShowAgainKey());
            assertContains(afterSecond.get(beforeSecond + 2).getMessage(), "OptiFine");
            assertContains(afterSecond.get(beforeSecond).getMessage(), "essential-container.jar");
            assertEquals("essential-container", afterSecond.get(beforeSecond).getAffectedMod().getModId());

            CrashAssistantLocalConfig.set("problematic_mod_bypass_essential-container", true);
            CrashAssistantLocalConfig.set("problematic_mod_bypass_e4mc", true);
            int beforeBypass = Startup.getCrashWarnings().size();
            executeResource(MODPACK_RULES_SCRIPT);
            List<ScriptWarning> afterBypass = Startup.getCrashWarnings();
            assertEquals(beforeBypass + 1, afterBypass.size());
            assertContains(afterBypass.get(beforeBypass).getMessage(), "OptiFine");

            CrashAssistantLocalConfig.clearAll();
            CrashAssistantConfig.set("text.modpack_name", "Different Pack - audit");
            int beforeWrongPack = Startup.getCrashWarnings().size();
            executeResource(MODPACK_RULES_SCRIPT);
            List<ScriptWarning> afterWrongPack = Startup.getCrashWarnings();
            assertEquals(beforeWrongPack + 2, afterWrongPack.size());
            assertEquals("essential-container", afterWrongPack.get(beforeWrongPack).getAffectedMod().getModId());
            assertContains(afterWrongPack.get(beforeWrongPack + 1).getMessage(), "OptiFine");

            for (String pageExample : PAGE_EXAMPLES) {
                executeResource(pageExample);
            }
        } finally {
            cacheField.set(null, oldCache);
            CrashAssistantConfig.set("text.modpack_name", oldModpackName);
            CrashAssistantLocalConfig.clearAll();
        }
    }

    private static Mod testMod(String modId, String version) {
        return new Mod(
                modId + ".jar", modId, modId, version, false, false,
                new HashSet<String>(), new ArrayList<Mod>(), null, null, null
        );
    }

    private static Object executeResource(String resource) throws Exception {
        return engine.createScript(readResource(resource)).execute(context);
    }

    private static void assertPrimitiveVarargsOutcome(String script, Object expectedResult) {
        try {
            assertEquals(expectedResult, execute(script));
        } catch (JexlException expected) {
            Throwable cause = expected;
            while (cause != null && !(cause instanceof ArrayStoreException)) {
                cause = cause.getCause();
            }
            assertTrue(cause instanceof ArrayStoreException,
                    "Only the known JEXL primitive-varargs packing failure is acceptable: " + expected);
        }
    }

    private static void testBundledExamplesCompile() throws Exception {
        compileResource("/META-INF/scripts/log_analysis/example.jexl");
        compileResource("/META-INF/scripts/startup/example.jexl");
    }

    private static void compileResource(String resource) throws Exception {
        engine.createScript(readResource(resource));
    }

    private static String readResource(String resource) throws Exception {
        try (InputStream stream = SecurityRegressionTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "Missing resource " + resource);
            StringBuilder script = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    script.append(line).append('\n');
                }
            }
            return script.toString();
        }
    }

    private static Object execute(String script) {
        return engine.createScript(script).execute(context);
    }

    private static void assertUnavailable(String script) {
        try {
            Object result = engine.createScript(script).execute(context);
            assertTrue(result == null, "Forbidden expression returned " + result + ": " + script);
        } catch (JexlException expected) {
            // An explicit JEXL denial is also the expected result.
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertContains(String actual, String expectedPart) {
        assertTrue(actual != null && actual.contains(expectedPart),
                "Expected text containing " + expectedPart + ", got " + actual);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
