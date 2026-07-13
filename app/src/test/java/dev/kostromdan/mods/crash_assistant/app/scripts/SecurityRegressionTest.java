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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;

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
        testPermissionManifestInvariants();
        setupJexl();
        testForbiddenClassRegistration();
        testForbiddenCapabilityResolution();
        testProductionPermissionsAndLanguageBasics();
        testDocumentedApi();
        JexlDocumentationCompatibilityTest.run(engine, context);
        testPrimitiveStreamVarargsCompatibility();
        testStartupScriptFixturesWithSyntheticMods();
        testBundledExamplesCompile();
        testUpstreamJexlSecurityCases();
        testLambdaSandboxing();
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
        context.set("forbiddenFileClass", java.io.File.class);
    }

    private static void testPermissionManifestInvariants() throws Exception {
        Set<String> entries = readPermissionEntries();

        assertOnlyPermissionEntries(entries, "java.io.",
                "java.io.BufferedReader",
                "java.io.ByteArrayInputStream",
                "java.io.ByteArrayOutputStream",
                "java.io.IOException",
                "java.io.InputStream",
                "java.io.InputStreamReader",
                "java.io.OutputStream",
                "java.io.Reader",
                "java.io.StreamTokenizer",
                "java.io.StringReader",
                "java.io.StringWriter",
                "java.io.Writer");
        assertOnlyPermissionEntries(entries, "java.net.",
                "java.net.URLDecoder",
                "java.net.URLEncoder");
        assertOnlyPermissionEntries(entries, "java.nio.",
                "java.nio.charset.Charset",
                "java.nio.charset.CodingErrorAction",
                "java.nio.charset.StandardCharsets");
        assertOnlyPermissionEntries(entries, "java.util.zip.",
                "java.util.zip.DataFormatException",
                "java.util.zip.Deflater",
                "java.util.zip.DeflaterOutputStream",
                "java.util.zip.GZIPInputStream",
                "java.util.zip.GZIPOutputStream",
                "java.util.zip.Inflater",
                "java.util.zip.InflaterInputStream",
                "java.util.zip.ZipException");

        assertNoPermissionPrefixes(entries,
                "java.awt.",
                "java.beans.",
                "java.lang.foreign.",
                "java.lang.instrument.",
                "java.lang.invoke.",
                "java.lang.management.",
                "java.lang.reflect.",
                "java.rmi.",
                "java.security.",
                "java.sql.",
                "java.util.jar.",
                "java.util.logging.",
                "java.util.prefs.",
                "javax.imageio.",
                "javax.management.",
                "javax.naming.",
                "javax.net.",
                "javax.script.",
                "javax.sql.",
                "javax.tools.",
                "javax.xml.",
                "jdk.",
                "org.w3c.dom.ls.",
                "org.xml.sax.",
                "sun.",
                "com.sun.",
                "okhttp3.",
                "org.apache.http.",
                "org.apache.commons.jexl3.");

        assertForbiddenPermissionEntries(entries,
                "java.lang.ClassLoader",
                "java.lang.ModuleLayer",
                "java.lang.Process",
                "java.lang.ProcessBuilder",
                "java.lang.ProcessHandle",
                "java.lang.Runtime",
                "java.lang.System",
                "java.lang.Thread",
                "java.lang.ThreadGroup",
                "java.util.ResourceBundle",
                "java.util.ServiceLoader",
                "java.util.Timer",
                "java.util.TimerTask",
                "java.util.spi.ToolProvider",
                "java.util.concurrent.CompletableFuture",
                "java.util.concurrent.Executor",
                "java.util.concurrent.ExecutorService",
                "java.util.concurrent.Executors",
                "java.util.concurrent.ForkJoinPool",
                "java.util.concurrent.ScheduledExecutorService",
                "java.util.concurrent.ScheduledThreadPoolExecutor",
                "java.util.concurrent.ThreadPoolExecutor",
                "crash_assistant_relocated_libs.jexl3.JexlBuilder",
                "crash_assistant_relocated_libs.jexl3.JexlEngine",
                "crash_assistant_relocated_libs.jexl3.internal.Engine",
                "crash_assistant_relocated_libs.jexl3.internal.introspection.Uberspect");

        Set<String> wildcardEntries = new HashSet<String>();
        for (String entry : entries) {
            if (entry.endsWith(".*")) {
                wildcardEntries.add(entry);
            }
        }
        assertEquals(1, wildcardEntries.size());
        assertTrue(wildcardEntries.contains("java.util.stream.*"),
                "Only the reviewed java.util.stream wildcard may be used in JEXL permissions");
    }

    private static void testForbiddenClassRegistration() {
        String[] forbiddenShortNames = {
                "Path", "Paths", "Files", "File", "FileSystems", "ZipFile", "JarFile",
                "URL", "URI", "URLConnection", "Socket", "ServerSocket", "DatagramSocket",
                "InetAddress", "HttpClient", "Runtime", "System", "Process", "ProcessBuilder",
                "ProcessHandle", "Thread", "ThreadGroup", "ClassLoader", "MethodHandles",
                "ServiceLoader", "ResourceBundle", "Timer", "Executors", "ForkJoinPool",
                "JexlBuilder", "JexlEngine", "Uberspect", "ScriptEngineManager", "InitialContext"
        };
        for (String shortName : forbiddenShortNames) {
            assertTrue(!Permissions.getClassMap().containsKey(shortName),
                    "Forbidden class registered in JEXL context: " + shortName);
        }
    }

    private static void testForbiddenCapabilityResolution() {
        context.set("probePath", Paths.get(".").toAbsolutePath());
        String[] forbiddenScripts = {
                "#pragma jexl.namespace.path java.nio.file.Path\npath:of('.')",
                "#pragma jexl.namespace.paths java.nio.file.Paths\npaths:get('.')",
                "#pragma jexl.namespace.files java.nio.file.Files\nfiles:exists(probePath)",
                "#pragma jexl.namespace.fileSystems java.nio.file.FileSystems\n" +
                        "fileSystems:getDefault()",
                "#pragma jexl.namespace.inet java.net.InetAddress\ninet:getLoopbackAddress()",
                "#pragma jexl.namespace.interfaces java.net.NetworkInterface\n" +
                        "interfaces:getNetworkInterfaces()",
                "#pragma jexl.namespace.proxies java.net.ProxySelector\nproxies:getDefault()",
                "#pragma jexl.namespace.http java.net.http.HttpClient\nhttp:newHttpClient()",
                "new('java.net.DatagramSocket')",
                "#pragma jexl.namespace.socketChannel java.nio.channels.SocketChannel\n" +
                        "socketChannel:open()",
                "#pragma jexl.namespace.datagramChannel java.nio.channels.DatagramChannel\n" +
                        "datagramChannel:open()",
                "#pragma jexl.namespace.thread java.lang.Thread\nthread:currentThread()",
                "#pragma jexl.namespace.process java.lang.ProcessHandle\nprocess:current()",
                "#pragma jexl.namespace.executors java.util.concurrent.Executors\n" +
                        "executors:newSingleThreadExecutor()",
                "new('java.util.concurrent.CompletableFuture')",
                "new('java.util.Formatter')",
                "#pragma jexl.namespace.loader java.lang.ClassLoader\n" +
                        "loader:getSystemClassLoader()",
                "#pragma jexl.namespace.handles java.lang.invoke.MethodHandles\nhandles:lookup()",
                "#pragma jexl.namespace.proxy java.lang.reflect.Proxy\nproxy:isProxyClass(String)",
                "new('javax.script.ScriptEngineManager')",
                "#pragma jexl.namespace.tools javax.tools.ToolProvider\ntools:getSystemJavaCompiler()"
        };
        try {
            for (String script : forbiddenScripts) {
                assertDenied(script);
            }
        } finally {
            context.set("probePath", null);
        }
    }

    private static void testProductionPermissionsAndLanguageBasics() {
        assertTrue(Permissions.isDevEnvironment(),
                "Security regression harness must exercise dev/unrelocated class names");
        assertEquals("org.apache.commons.jexl3.JexlScript",
                Permissions.getClassMap().get("JexlScript").getName());
        assertEquals("b", execute("'a:b'.split(':')[1]"));
        assertEquals(2, execute("arraysList.size()"));
        assertEquals(2, execute("Arrays.asList('a', 'b').size()"));
        assertEquals("x", execute("Collections.singletonList('x').get(0)"));
        assertEquals(2, execute("Collections.unmodifiableList(Arrays.asList('a', 'b')).size()"));
        assertEquals(Boolean.TRUE, execute("Collectors.toList() != null"));
        assertEquals(2L, execute("Stream.of('a', 'b').count()"));
        assertEquals(0L, execute("syncSet.stream().count()"));
        assertEquals("key", execute("singletonMap.keySet().iterator().next()"));
        assertTrue(execute("ZoneId.systemDefault().getId()") instanceof String,
                "Scripts must be able to read the current time zone");
        assertEquals(Boolean.TRUE,
                execute("ZoneRulesProvider.getAvailableZoneIds().contains('UTC')"));
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

    /**
     * Regression cases adapted from the security-relevant tests in Apache Commons JEXL 3.7.0:
     * PermissionsTest, Issues400Test, ClassCreatorTest, PragmaTest and PropertyAccessTest.
     */
    private static void testUpstreamJexlSecurityCases() throws Exception {
        assertDenied("Runtime.getRuntime()");
        assertDenied("System.getProperty('user.home')");
        assertDenied("new('java.lang.Thread')");
        assertDenied("new('java.util.Timer', true)");

        Path archiveCanary = Paths.get("security-archive-canary.jar").toAbsolutePath();
        Files.deleteIfExists(archiveCanary);
        try {
            try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(archiveCanary))) {
            }
            context.set("forbiddenPath", archiveCanary);
            String archivePath = jexlString(archiveCanary.toString());
            assertDenied("import java.io.FileWriter; new FileWriter(" + archivePath + ")");
            assertDenied("import java.io.FileReader; new FileReader(" + archivePath + ")");
            assertDenied("new('java.util.zip.ZipFile', " + archivePath + ")");
            assertDenied("new('java.util.jar.JarFile', " + archivePath + ")");
            assertDenied("#pragma jexl.namespace.files java.nio.file.Files\n" +
                    "files:exists(forbiddenPath)");
        } finally {
            context.set("forbiddenPath", null);
            Files.deleteIfExists(archiveCanary);
        }
        assertDenied("new java.io.File('forbidden')");
        assertDenied("import java.io.PrintWriter; new PrintWriter(new('java.io.StringWriter'))");
        assertDenied("new(forbiddenFileClass, 'forbidden')");

        assertDenied("''.class.forName('java.io.File')");
        assertDenied("''.class.classLoader");
        assertDenied("''['class'].forName('java.net.URL')");
        assertDenied("''.class.declaredMethods");
        assertDenied("''.class.protectionDomain");
        assertDenied("''.class.getResourceAsStream('/jexl_allowed_classes.txt')");

        assertDenied("#pragma jexl.namespace.runtime java.lang.Runtime\nruntime:getRuntime()");
        assertDenied("#pragma jexl.namespace.sys java.lang.System\nsys:getProperty('user.home')");
        assertDenied("#pragma jexl.namespace.services java.util.ServiceLoader\nservices:load(String)");
        assertDenied("#pragma jexl.namespace.bundles java.util.ResourceBundle\n" +
                "bundles:clearCache()");
        assertDenied("new('org.apache.commons.jexl3.JexlBuilder')");
        assertDenied("new('org.apache.commons.jexl3.internal.introspection.Uberspect', null, null)" +
                ".getClassLoader().loadClass('java.lang.System')");

        String integerProperty = "crash_assistant.security.integer";
        String longProperty = "crash_assistant.security.long";
        String booleanProperty = "crash_assistant.security.boolean";
        System.setProperty(integerProperty, "7");
        System.setProperty(longProperty, "9");
        System.setProperty(booleanProperty, "true");
        try {
            assertEquals(7, execute("Integer.getInteger('" + integerProperty + "')"));
            assertEquals(9L, execute("Long.getLong('" + longProperty + "')"));
            assertEquals(Boolean.TRUE,
                    execute("Boolean.getBoolean('" + booleanProperty + "')"));
        } finally {
            System.clearProperty(integerProperty);
            System.clearProperty(longProperty);
            System.clearProperty(booleanProperty);
        }
    }

    private static void testLambdaSandboxing() throws Exception {
        assertEquals(2, execute("var f = x -> x + 1; f(1)"));
        assertEquals(3, execute(
                "var outer = x -> { var inner = y -> y + 1; inner(x) }; outer(2)"
        ));
        assertEquals(2, execute("Stream.of(1).map(x -> x + 1).findFirst().get()"));
        assertEquals(1024, execute(
                "IntStream.range(0, 1024).boxed().parallel().map(x -> x + 1).toList().size()"
        ));

        Path lambdaFile = Paths.get("forbidden-lambda-file").toAbsolutePath();
        Files.deleteIfExists(lambdaFile);
        String[] restrictedBodies = {
                "new('java.io.FileOutputStream', " + jexlString(lambdaFile.toString()) + ")",
                "new('java.net.Socket')",
                "new('java.lang.ProcessBuilder', 'forbidden-command')",
                "''.getClass().getDeclaredMethods()",
                "''.getClass().getClassLoader()"
        };

        try {
            for (String restrictedBody : restrictedBodies) {
                assertDenied("var f = x -> " + restrictedBody + "; f(1)");
                assertDenied("var outer = x -> { var inner = y -> " + restrictedBody +
                        "; inner(x) }; outer(1)");
                assertDenied("Stream.of(1).map(x -> " + restrictedBody + ").findFirst()");
                assertDenied("IntStream.range(0, 1024).boxed().parallel().forEach(x -> " +
                        restrictedBody + ")");
            }

            // Closure and Script are intentionally visible for lambda support. Their internal Engine is not.
            assertDenied("var f = () -> 42; f.getEngine().createScript('42')");
            assertDenied("var f = () -> 42; " +
                    "f.getEngine().newInstance(forbiddenFileClass, 'forbidden')");
            assertTrue(Files.notExists(lambdaFile),
                    "Denied lambda must not create its file-system canary");
        } finally {
            Files.deleteIfExists(lambdaFile);
        }
    }

    private static void testFileAndNetworkAccess() throws Exception {
        context.set("testLog", new Log(LogType.LOG, "test", Paths.get("not-readable-by-script.log")));

        Path inputCanary = Paths.get("security-input-canary.properties").toAbsolutePath();
        Path outputCanary = Paths.get("security-output-canary.txt").toAbsolutePath();
        Files.write(inputCanary, "secret=must-not-be-readable".getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(outputCanary);
        context.set("forbiddenInputPath", inputCanary);
        context.set("forbiddenOutputPath", outputCanary);

        try {
            String inputPath = jexlString(inputCanary.toString());
            String outputPath = jexlString(outputCanary.toString());

            assertDenied("testLog.getPath()");
            assertDenied("testLog.getFile()");
            assertDenied("CrashAssistantConfig.getConfigPath()");
            assertDenied("ModListUtils.MODS_FOLDER");
            assertDenied("LanguageProvider.OPTIONS_PATH");
            assertDenied("new('dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod', 'fake.jar')");
            assertDenied("#pragma jexl.namespace.path java.nio.file.Path\n" +
                    "path:of(" + inputPath + ").toRealPath()");
            assertDenied("#pragma jexl.namespace.path java.nio.file.Path\n" +
                    "new('java.util.Scanner', path:of(" + inputPath + ")).next()");
            assertDenied("#pragma jexl.namespace.files java.nio.file.Files\n" +
                    "files:readString(forbiddenInputPath)");
            assertDenied("#pragma jexl.namespace.files java.nio.file.Files\n" +
                    "files:writeString(forbiddenOutputPath, 'forbidden')");
            assertDenied("#pragma jexl.namespace.fileChannel java.nio.channels.FileChannel\n" +
                    "fileChannel:open(forbiddenInputPath)");
            assertDenied("#pragma jexl.namespace.files java.nio.file.Files\n" +
                    "var p = new('java.util.Properties'); " +
                    "p.load(files:newInputStream(forbiddenInputPath)); p");
            assertDenied("new('java.util.Formatter', forbiddenOutputPath.toFile())" +
                    ".format('forbidden').close()");
            assertDenied("new('java.io.File', " + inputPath + ")");
            assertDenied("new('java.io.FileInputStream', " + inputPath + ")");
            assertDenied("new('java.io.FileOutputStream', " + outputPath + ")");
            assertDenied("new('java.io.FileReader', " + inputPath + ")");
            assertDenied("new('java.io.FileWriter', " + outputPath + ")");
            assertDenied("new('java.io.RandomAccessFile', " + outputPath + ", 'rw')");
            assertDenied("new('java.io.PrintWriter', " + outputPath + ")");
            assertDenied("new('java.util.Formatter', " + outputPath + ")");
            assertDenied("var p = new('java.util.Properties'); " +
                    "p.load(new('java.io.FileInputStream', " + inputPath + ")); p");
            assertDenied("var p = new('java.util.Properties'); " +
                    "p.store(new('java.io.FileOutputStream', " + outputPath + "), 'forbidden')");
            assertDenied("new('java.util.Scanner', new('java.io.File', " + inputPath + ")).next()");
            assertDenied("Stream.of(1).map(x -> new('java.io.File', " + inputPath + ")).findFirst()");
            assertDenied("#pragma jexl.import java.io\nnew('File', " + inputPath + ")");

            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                String loopback = listener.getInetAddress().getHostAddress();
                int port = listener.getLocalPort();
                assertDenied("new('java.net.Socket', " + jexlString(loopback) + ", " + port + ")");
                assertDenied("new('java.net.URL', 'http://" + loopback + ":" + port +
                        "/canary').openConnection().connect()");
            }

            assertDenied("new('java.net.URL', 'https://example.invalid')");
            assertDenied("new('java.net.URI', 'https://example.invalid')");
            assertDenied("new('java.net.Socket')");
            assertDenied("new('java.net.ServerSocket')");
            assertDenied("new('java.lang.ProcessBuilder', 'forbidden-command')");
            assertDenied("new('java.util.concurrent.ForkJoinPool')");
            assertDenied("Stream.of(1).parallel().map(x -> " +
                    "new('java.net.URL', 'https://example.invalid')).findFirst()");
            assertDenied("Integer.TYPE.getClassLoader()");
            assertDenied("Integer.TYPE.getDeclaredMethods()");
            assertDenied("''.getClass().forName('java.io.File')");
            assertDenied("''.getClass().getProtectionDomain()");
            assertDenied("PlatformHelp.FORGE.getClass().getClassLoader()");
            assertDenied("foreignMap.size()");
            assertDenied("foreignMap.entrySet()");

            assertTrue(Files.notExists(outputCanary),
                    "Denied file-system operations must not create their output canary");
        } finally {
            context.set("forbiddenInputPath", null);
            context.set("forbiddenOutputPath", null);
            Files.deleteIfExists(inputCanary);
            Files.deleteIfExists(outputCanary);
        }
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

    private static Set<String> readPermissionEntries() throws Exception {
        Set<String> entries = new HashSet<String>();
        try (InputStream stream = SecurityRegressionTest.class.getResourceAsStream(
                "/jexl_allowed_classes.txt")) {
            assertTrue(stream != null, "Missing /jexl_allowed_classes.txt");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String entry = line.trim();
                    if (!entry.isEmpty() && !entry.startsWith("#")) {
                        entries.add(entry);
                    }
                }
            }
        }
        return entries;
    }

    private static void assertOnlyPermissionEntries(Set<String> entries, String prefix,
                                                    String... allowedEntries) {
        Set<String> safeEntries = new HashSet<String>(Arrays.asList(allowedEntries));
        for (String entry : entries) {
            if (entry.startsWith(prefix) && !safeEntries.contains(entry)) {
                throw new AssertionError("Unreviewed capability in JEXL permissions: " + entry);
            }
        }
    }

    private static void assertNoPermissionPrefixes(Set<String> entries, String... prefixes) {
        for (String entry : entries) {
            for (String prefix : prefixes) {
                if (entry.startsWith(prefix)) {
                    throw new AssertionError("Forbidden capability in JEXL permissions: " + entry);
                }
            }
        }
    }

    private static void assertForbiddenPermissionEntries(Set<String> entries,
                                                         String... forbiddenEntries) {
        for (String forbiddenEntry : forbiddenEntries) {
            if (entries.contains(forbiddenEntry)) {
                throw new AssertionError("Forbidden JEXL class: " + forbiddenEntry);
            }
        }
    }

    private static Object execute(String script) {
        return engine.createScript(script).execute(context);
    }

    private static void assertDenied(String script) {
        try {
            Object result = engine.createScript(script).execute(context);
            if (result instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) result).close();
                } catch (Exception ignored) {
                }
            }
            throw new AssertionError("Forbidden expression completed normally with result " +
                    result + ": " + script);
        } catch (JexlException expected) {
            logPermissionTestException(script, expected);
            if (expected instanceof JexlException.Parsing
                    || expected instanceof JexlException.Tokenization
                    || expected instanceof JexlException.Feature) {
                throw new AssertionError("Security case must parse successfully before being denied: " +
                        script, expected);
            }
            if (expected.getCause() != null) {
                throw new AssertionError("Security case was rejected by an underlying operation, " +
                        "not by the sandbox: " + script, expected);
            }
            if (permissionDenialKind(script, expected) == null) {
                throw new AssertionError("Unexpected JEXL exception in security case: " + script,
                        expected);
            }
        }
    }

    private static void logPermissionTestException(String script, JexlException exception) {
        System.out.println("[permission-test] script: " + script.replace('\n', ' '));
        System.out.println("[permission-test] classification: " +
                permissionDenialKind(script, exception));
        Throwable current = exception;
        int depth = 0;
        while (current != null) {
            System.out.println("[permission-test] exception[" + depth + "]: " +
                    current.getClass().getName() + ": " + current.getMessage());
            current = current.getCause();
            depth++;
        }
    }

    private static String permissionDenialKind(String script, JexlException exception) {
        if (exception instanceof JexlException.Variable) {
            if ("Runtime.getRuntime()".equals(script)
                    || "System.getProperty('user.home')".equals(script)) {
                return "WITHHELD_CONTEXT_SYMBOL";
            }
            return null;
        }
        if (exception instanceof JexlException.Method) {
            return "FILTERED_METHOD_OR_CONSTRUCTOR";
        }
        if (exception instanceof JexlException.Property) {
            return "FILTERED_PROPERTY";
        }
        if (exception.getClass() == JexlException.class
                && exception.getMessage() != null
                && exception.getMessage().contains("no such function namespace")) {
            return "FILTERED_NAMESPACE";
        }
        return null;
    }

    private static String jexlString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
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
