package dev.kostromdan.mods.crash_assistant.app.scripts;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.class_loading.Boot;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantLocalConfig;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.GeneratedMessage;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptUtils;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptWarning;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.Startup;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class JexlDocumentationCompatibilityTest {
    private final JexlEngine engine;
    private final JexlContext context;
    private final Path workDir;

    private JexlDocumentationCompatibilityTest(JexlEngine engine, JexlContext context) throws IOException {
        this.engine = engine;
        this.context = context;
        this.workDir = Paths.get("documentation-cases").toAbsolutePath();
        Files.createDirectories(workDir);
        for (LogType type : LogType.values()) {
            context.set(type.name(), type);
        }
    }

    static void run(JexlEngine engine, JexlContext context) throws Exception {
        JexlDocumentationCompatibilityTest test = new JexlDocumentationCompatibilityTest(engine, context);
        test.testStaticFieldsAndEnvironment();
        test.testLogsAndRegex();
        test.testHsErrParsing();
        test.testVersionUtils();
        test.testAnalysisAndGlobalState();
        test.testScriptWarningConfiguration();
        test.testStartupApi();
        test.testLoggers();
        test.testMemoryUtils();
        test.testArgumentsAndLocalConfig();
        test.testMods();
        test.testJavaCollectionsRegexAndStreams();
    }

    private void testStaticFieldsAndEnvironment() {
        String oldLoader = PlatformHelp.loaderJarName;
        String oldJvmArgs = Boot.MINECRAFT_JVM_ARGS;
        String oldLaunch = Boot.MINECRAFT_LAUNCH_COMMAND;
        String oldXms = CrashAssistantApp.minecraftXms;
        String oldXmx = CrashAssistantApp.minecraftXmx;
        String oldProcessor = CrashAssistantApp.processor;
        String oldRenderer = CrashAssistantApp.renderer;
        try {
            PlatformHelp.loaderJarName = "loader-static-field";
            Boot.MINECRAFT_JVM_ARGS = "minecraft-jvm-args";
            Boot.MINECRAFT_LAUNCH_COMMAND = "minecraft-launch-command";
            CrashAssistantApp.minecraftXms = "512m";
            CrashAssistantApp.minecraftXmx = "2g";
            CrashAssistantApp.processor = "test-cpu";
            CrashAssistantApp.renderer = "test-gpu";

            assertEquals("loader-static-field", execute("PlatformHelp.loaderJarName"));
            assertEquals("minecraft-jvm-args", execute("Boot.MINECRAFT_JVM_ARGS"));
            assertEquals("minecraft-launch-command", execute("Boot.MINECRAFT_LAUNCH_COMMAND"));
            assertEquals("512m", execute("CrashAssistantApp.minecraftXms"));
            assertEquals("2g", execute("CrashAssistantApp.minecraftXmx"));
            assertEquals("test-cpu", execute("CrashAssistantApp.processor"));
            assertEquals("test-gpu", execute("CrashAssistantApp.renderer"));

            assertType(Boolean.class, execute("PlatformHelp.isWindows()"));
            assertType(Boolean.class, execute("PlatformHelp.isMacOS()"));
            assertType(Boolean.class, execute("PlatformHelp.isLinux()"));
            assertTrue(execute("new('java.lang.Object')") != null, "Object constructor must stay available");
            assertEquals("java.lang.String", execute("''.getClass().getName()"));
            assertEquals("String", execute("''.getClass().getSimpleName()"));
            assertEquals("java.lang.String", execute("''.class.name"));
            assertEquals(Boolean.TRUE, execute("ClassExistenceChecker.classExists('java.lang.String')"));
            assertEquals(Boolean.FALSE, execute("ClassExistenceChecker.classExists('invalid.documentation.Missing')"));
            assertEquals("LOG", execute("LOG.name()"));
            assertType(Number.class, execute("Pattern.CASE_INSENSITIVE"));
        } finally {
            PlatformHelp.loaderJarName = oldLoader;
            Boot.MINECRAFT_JVM_ARGS = oldJvmArgs;
            Boot.MINECRAFT_LAUNCH_COMMAND = oldLaunch;
            CrashAssistantApp.minecraftXms = oldXms;
            CrashAssistantApp.minecraftXmx = oldXmx;
            CrashAssistantApp.processor = oldProcessor;
            CrashAssistantApp.renderer = oldRenderer;
        }
    }

    private void testLogsAndRegex() throws Exception {
        Path path = workDir.resolve("documented.log");
        Files.write(path, Arrays.asList("first line", "Fabric loader version: 1.2.3", "last line"), StandardCharsets.UTF_8);
        Log log = new Log(LogType.LOG, "Documentation: documented.log", path);
        log.getReader().readLogFileSafe();
        context.set("docLog", log);

        assertEquals("Documentation: documented.log", execute("docLog.getName()"));
        assertEquals("documented.log", execute("docLog.getFileName()"));
        assertEquals(LogType.LOG, execute("docLog.getType()"));
        assertTrue(execute("docLog.getReader()") != null, "LogReader must be accessible");
        assertContains((String) execute("docLog.getReader().getAllLinesString()"), "Fabric loader version");
        assertEquals(3, ((List<?>) execute("docLog.getReader().getAllLinesList()")).size());
        assertEquals(2, ((List<?>) execute("docLog.getReader().getFirstNLines(2)")).size());
        assertEquals(2, ((List<?>) execute("docLog.getReader().getLastNLines(2)")).size());
        assertEquals("first line", execute("docLog.getReader().getFirstLine()"));
        assertEquals("last line", execute("docLog.getReader().getLastLine()"));
        assertEquals(Boolean.TRUE, execute("RegexChecker.logContainsOneOfPatterns(docLog, 'missing', 'Fabric loader version: .*')"));
        assertEquals("1.2.3", execute(
                "var matcher = Pattern.compile('Fabric loader version: (\\\\d+\\\\.\\\\d+\\\\.\\\\d+)')" +
                        ".matcher(docLog.reader.allLinesString); " +
                        "matcher.find(); matcher.group(1).trim()"
        ));
        assertEquals("Fabric loader version: 1.2.3", execute(
                "var lines = docLog.reader.allLinesList; var found = ''; " +
                        "for (var i = 0; i < lines.size(); i++) { " +
                        "if (lines.get(i).contains('Fabric loader')) { found = lines.get(i); break; } } found"
        ));

        assertTrue(execute("LogsList.getLogs()") instanceof java.util.Set, "LogsList.getLogs() must return Set");
        assertTrue(execute("LogsList.getLogs(LOG, CRASH_REPORT)") instanceof List, "Varargs LogType filtering must return List");
        assertType(Number.class, execute("LogsList.getLogs().stream().count()"));
    }

    private void testHsErrParsing() throws Exception {
        Path path = workDir.resolve("hs_err_pid1.log");
        Files.write(path, Arrays.asList(
                "# Problematic frame:",
                "# C  [nvoglv64.dll+0x123] [nvwgf2umx.dll]",
                "Memory: 4k page, physical 16384M (8192M free)",
                "TotalPageFile size 32768M (AvailPageFile size 16000M)"
        ), StandardCharsets.UTF_8);
        Log log = new Log(LogType.HS_ERR, "hs_err", path);
        log.getReader().readLogFileSafe();
        context.set("hsLog", log);

        assertEquals(Boolean.TRUE, execute("HsErrParser.hsErrContainsOneOfFrames(hsLog, 'missing.dll', 'nvoglv64.dll')"));
        assertEquals(Boolean.TRUE, execute("HsErrParser.hsErrContainsAllOfFrames(hsLog, 'nvoglv64.dll', 'nvwgf2umx.dll')"));
    }

    private void testVersionUtils() {
        assertEquals(Boolean.TRUE, execute("VersionUtils.isLower('1.0', '2.0')"));
        assertEquals(Boolean.TRUE, execute("VersionUtils.isLowerThanOrEqual('2.0', '2.0')"));
        assertEquals(Boolean.TRUE, execute("VersionUtils.isGreater('2.0', '1.0')"));
        assertEquals(Boolean.TRUE, execute("VersionUtils.isGreaterThanOrEqual('2.0', '2.0')"));
        assertEquals(Boolean.TRUE, execute("VersionUtils.isEqual('1.0.0', '1.0')"));
        assertEquals(Boolean.TRUE, execute("VersionUtils.inRange('2.0', '1.0', '3.0')"));
    }

    private void testAnalysisAndGlobalState() {
        Analysis.getRegisteredWarnings().clear();
        GeneratedMessage.reset();
        context.set("docLog", context.get("docLog"));
        assertTrue(execute("Analysis.addWarning('global documentation warning')") instanceof ScriptWarning,
                "Global Analysis warning must be created");
        assertTrue(execute("Analysis.addWarning(docLog, 'log documentation warning')") instanceof ScriptWarning,
                "Log Analysis warning must be created");

        ScriptUtils.setCurrentScriptName("documentation-script.jexl");
        execute("Analysis.markRunAlways()");
        assertTrue(ScriptUtils.isAlwaysRun("documentation-script.jexl"), "markRunAlways must mark current script");
        ScriptUtils.setCurrentScriptName(null);

        execute("Analysis.setGlobal('documentation-key', 'documentation-value')");
        assertEquals("documentation-value", execute("Analysis.getGlobal('documentation-key')"));
        assertEquals("documentation-value", execute("Startup.getGlobal('documentation-key')"));
        execute("Startup.setGlobal('startup-documentation-key', 'startup-value')");
        assertEquals("startup-value", execute("Analysis.getGlobal('startup-documentation-key')"));
        assertType(String.class, execute("LanguageProvider.get('custom.manual_crash')"));

        ScriptUtils.setCurrentScriptName("log_analysis/documentation.jexl");
        execute("Analysis.putCopiedResult('documented-result', 'Copied documentation result', 10)");
        assertEquals(Arrays.asList("Copied documentation result"), GeneratedMessage.getAnalysisResults());
        execute("Analysis.removeCopiedResult('documented-result')");
        assertTrue(GeneratedMessage.getAnalysisResults().isEmpty(), "Copied result must be removable");
        ScriptUtils.setCurrentScriptName(null);
        GeneratedMessage.reset();
    }

    private void testScriptWarningConfiguration() {
        Mod mod = createMod();
        ScriptWarning warning = new ScriptWarning("documentation warning");
        context.set("docMod", mod);
        context.set("docWarning", warning);

        Object result = execute(
                "docWarning.withPriority(12345)" +
                        ".withDontShowAgain('arbitrary key/with spaces')" +
                        ".withCustomDontShowAgainCheckboxText('custom checkbox')" +
                        ".withOkDelay(7)" +
                        ".withModActions(docMod)" +
                        ".withRemoveButton(false)" +
                        ".withDisableButton(false)" +
                        ".withExplorerButton(false)" +
                        ".withKillMinecraftButton(true)" +
                        ".withShowModListDiffButton()" +
                        ".addGuideButton('Custom guide', 'https://untrusted.example/guide')" +
                        ".withMemoryAllocationGuide()" +
                        ".withJvmArgsGuide()" +
                        ".withJavaVersionGuide()"
        );
        assertEquals(warning, result);
        assertEquals(12345, warning.getPriority());
        assertEquals("arbitrary key/with spaces", warning.getDontShowAgainKey());
        assertEquals("custom checkbox", warning.getDontShowAgainCheckboxText());
        assertEquals(7, warning.getOkDelay());
        assertEquals(mod, warning.getAffectedMod());
        assertEquals(false, warning.isShowRemoveButton());
        assertEquals(false, warning.isShowDisableButton());
        assertEquals(false, warning.isShowExplorerButton());
        assertEquals(true, warning.isShowKillMinecraftButton());
        assertEquals(true, warning.isShowModListDiffButton());
        assertEquals(4, warning.getGuideButtons().size());
        assertEquals("https://untrusted.example/guide", warning.getGuideButtons().get(0)[1]);
    }

    private void testStartupApi() {
        int bootCount = Startup.getBootWarnings().size();
        int crashCount = Startup.getCrashWarnings().size();
        assertTrue(execute("Startup.addBootWarning('boot documentation warning')") instanceof ScriptWarning,
                "Boot warning must be created");
        assertTrue(execute("Startup.addCrashWarning('crash documentation warning')") instanceof ScriptWarning,
                "Crash warning must be created");
        assertEquals(bootCount + 1, Startup.getBootWarnings().size());
        assertEquals(crashCount + 1, Startup.getCrashWarnings().size());
        execute("Startup.markForCrash()");
        assertTrue(Startup.isMarkedForCrash(), "Startup.markForCrash must remain callable");
    }

    private void testLoggers() {
        execute("Logger.info('documentation logger info')");
        execute("Logger.warn('documentation logger value={}', 2)");
        execute("Logger.error('documentation logger error')");
        assertType(RuntimeException.class, execute("new('java.lang.RuntimeException', 'cause')"));
    }

    private void testMemoryUtils() {
        assertType(Number.class, execute("MemoryUtils.getJvmInitialHeapBytes()"));
        assertType(Number.class, execute("MemoryUtils.getJvmMaxHeapBytes()"));
        assertType(Number.class, execute("MemoryUtils.getJvmAllocatedMemoryBytes()"));
        assertType(Number.class, execute("MemoryUtils.getSystemTotalMemoryBytes()"));
        assertType(Number.class, execute("MemoryUtils.getSystemUsedMemoryBytes()"));
        assertType(Number.class, execute("MemoryUtils.getSystemFreeMemoryBytes()"));
        assertType(Number.class, execute("MemoryUtils.getSystemTotalSwapBytes()"));
        assertType(Number.class, execute("MemoryUtils.getSystemUsedSwapBytes()"));
        assertType(Number.class, execute("MemoryUtils.getSystemFreeSwapBytes()"));
        assertEquals(1.0d, execute("MemoryUtils.bytesToMegabytes(1048576)"));
        assertEquals(1.0d, execute("MemoryUtils.bytesToGigabytes(1073741824)"));
        assertEquals("512m", execute("MemoryUtils.formatMemorySize(536870912)"));
        assertEquals(536870912L, execute("MemoryUtils.parseMemorySize('512m')"));
        assertEquals("2.5g", execute("MemoryUtils.formatMemorySize(2684354560)"));
        assertEquals(2684354560L, execute("MemoryUtils.parseMemorySize('2.5g')"));
    }

    private void testArgumentsAndLocalConfig() {
        ArgUtils.setLaunchArgs("--accessToken=secret");
        assertEquals("--accessToken=????????", execute("ArgUtils.getSafeLaunchArgs()"));
        assertType(String.class, execute("ArgUtils.getSafeJvmArgs()"));
        assertTrue(execute("ArgUtils.getSafeClassPathList()") instanceof String[], "Allowed arrays must remain usable");
        ArgUtils.setLaunchArgs((String) null);

        execute("CrashAssistantLocalConfig.set('documentation.arbitrary.key', true)");
        assertEquals(Boolean.TRUE, execute("CrashAssistantLocalConfig.get('documentation.arbitrary.key')"));
        CrashAssistantLocalConfig.clearAll();
    }

    private void testMods() {
        Mod mod = createMod();
        context.set("docMod", mod);
        assertEquals("documented.jar", execute("docMod.getJarName()"));
        assertEquals("documented", execute("docMod.getModId()"));
        assertEquals("1.2.3", execute("docMod.getVersion()"));
        assertEquals(Boolean.TRUE, execute("docMod.IsMCreator()"));
        assertEquals("documented.jar", execute("docMod.jarName"));
        assertEquals("documented", execute("docMod.modId"));
        assertTrue(execute("ModListUtils.getCurrentModList(true)") instanceof java.util.LinkedHashSet,
                "Current mod list must be a LinkedHashSet");
    }

    private void testJavaCollectionsRegexAndStreams() {
        assertEquals(5, execute("Math.max(2, 5)"));
        assertEquals("x", execute("Collections.singletonList('x').get(0)"));
        assertEquals(2, execute("new('java.util.ArrayList', Arrays.asList('a', 'b')).size()"));
        assertEquals(2, execute("Arrays.asList('a', 'b').size()"));
        assertEquals(2, execute("Collections.unmodifiableList(Arrays.asList('a', 'b')).size()"));
        assertEquals(2, execute(
                "var map = new('java.util.HashMap'); map.put('a', 1); map.put('b', 2); map.keySet().size()"
        ));
        assertEquals(2, execute(
                "var map = new('java.util.HashMap'); map.put('a', 1); map.put('b', 2); map.values().size()"
        ));
        assertEquals(2, execute(
                "var map = new('java.util.HashMap'); map.put('a', 1); map.put('b', 2); map.entrySet().size()"
        ));
        assertEquals("a", execute(
                "var list = new('java.util.ArrayList'); list.add('a'); list.iterator().next()"
        ));
        assertEquals("key", execute(
                "var map = new('java.util.HashMap'); map.put('key', 'value'); map.keySet().iterator().next()"
        ));
        assertEquals("value", execute(
                "var map = new('java.util.HashMap'); map.put('key', 'value'); map.values().iterator().next()"
        ));
        assertEquals("key", execute(
                "var map = new('java.util.HashMap'); map.put('key', 'value'); map.entrySet().iterator().next().getKey()"
        ));
        assertEquals(0, execute("Collections.emptyList().size()"));
        assertEquals(1, execute("Collections.singletonMap('key', 'value').size()"));
        assertEquals(1, execute("Collections.singletonMap('key', 'value').keySet().size()"));
        assertEquals("key", execute("Collections.singletonMap('key', 'value').keySet().iterator().next()"));
        assertEquals(1, execute("EnumSet.of(DayOfWeek.MONDAY).size()"));
        assertEquals(65, execute("EnumSet.allOf(largeEnumClass).size()"));
        assertEquals("key", execute("Map.entry('key', 'value').getKey()"));
        assertTrue(((Number) execute("Comparator.naturalOrder().compare('a', 'b')")).intValue() < 0,
                "Comparator factory implementation must remain callable");
        assertTrue(((Number) execute("Comparator.nullsFirst(Comparator.naturalOrder()).compare(null, 'a')")).intValue() < 0,
                "Nested comparator implementations must remain callable");
        assertEquals("key", execute("new('java.util.AbstractMap$SimpleEntry', 'key', 'value').getKey()"));
        assertEquals("demo", execute("'prefix: demo'.split(': ')[1]"));
        assertEquals("first", execute("new('java.util.Scanner', 'first second').next()"));
        assertEquals("value", execute(
                "var properties = new('java.util.Properties'); " +
                        "properties.setProperty('key', 'value'); properties.getProperty('key')"
        ));
        assertEquals(Boolean.TRUE, execute(
                "var properties = new('java.util.Properties'); properties.setProperty('key', 'value'); " +
                        "var output = new('java.io.ByteArrayOutputStream'); " +
                        "properties.store(output, 'memory-only'); output.size() > 0"
        ));
        assertEquals("1.2.3", execute("Pattern.compile('version: (\\\\d+\\\\.\\\\d+\\\\.\\\\d+)').matcher('version: 1.2.3').replaceAll('$1')"));
        assertEquals(2L, execute("Stream.of(1, 2, 3).filter(x -> x > 1).count()"));
        assertEquals(12, execute("Stream.of(1, 2, 3).map(x -> x * 2).reduce(0, (a, b) -> a + b)"));
        assertEquals(6L, execute("Stream.of(1, 2, 3).flatMap(x -> Stream.of(x, x)).count()"));
        assertEquals("a,b", execute("Stream.of('a', 'b').collect(Collectors.joining(','))"));
        assertEquals(6, execute("IntStream.rangeClosed(1, 3).sum()"));
        assertEquals(6L, execute("LongStream.rangeClosed(1, 3).sum()"));
        assertEquals(2.0d, execute("DoubleStream.builder().add(1).add(2).add(3).build().average().getAsDouble()"));
        assertEquals(Boolean.TRUE, execute("Stream.of(1).parallel().isParallel()"));
        assertEquals(5, execute("Stream.of(1, 5, 3).parallel().map(x -> Math.max(x, 2)).max((a, b) -> Integer.compare(a, b))"));
        assertEquals(3, execute("Stream.of(1, 2, 3).toList().size()"));
        assertEquals(2, execute("List.of('a', 'b').size()"));
        assertEquals(1, execute("Set.of('a').size()"));
        assertEquals("value", execute("Map.of('key', 'value').get('key')"));

        Object mapped = execute(
                "var allMods = new('java.util.LinkedHashSet'); " +
                        "allMods.add(docMod); " +
                        "allMods.stream().toMap(m -> m.modId, m -> m)"
        );
        assertTrue(mapped instanceof Map, "Documented stream().toMap must return Map");
        assertEquals("documented.jar", ((Mod) ((Map<?, ?>) mapped).get("documented")).getJarName());

        assertEquals("ab", execute(
                "var values = ['a', 'b']; var joined = ''; " +
                        "for (var value : values) { joined = joined + value; } joined"
        ));
    }

    private Mod createMod() {
        return new Mod(
                "documented.jar",
                "documented",
                "Documented Mod",
                "1.2.3",
                true,
                false,
                new HashSet<String>(),
                new ArrayList<Mod>(),
                null,
                null,
                null
        );
    }

    private Object execute(String script) {
        return engine.createScript(script).execute(context);
    }

    private static void assertContains(String actual, String expectedPart) {
        assertTrue(actual != null && actual.contains(expectedPart), "Expected text containing " + expectedPart + ", got " + actual);
    }

    private static void assertType(Class<?> expectedType, Object actual) {
        assertTrue(actual != null && expectedType.isInstance(actual),
                "Expected " + expectedType.getName() + ", got " + (actual == null ? "null" : actual.getClass().getName()));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
