package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions.Permissions;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.GeneratedMessage;
import dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils.ScriptUtils;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StartupDocumentationCompatibilityTest {
    private StartupDocumentationCompatibilityTest() {
    }

    public static void main(String[] args) {
        if (!Permissions.isDevEnvironment()) {
            throw new AssertionError("Startup test harness must exercise dev/unrelocated class names");
        }
        JexlEngine engine = Permissions.getEngine();
        JexlContext context = new MapContext();
        for (Map.Entry<String, Class<?>> entry : Permissions.getClassMap().entrySet()) {
            context.set(entry.getKey(), entry.getValue());
        }

        execute(engine, context,
                "Logger.info('startup logger info'); " +
                        "Logger.warn('startup logger value={}', 2); " +
                        "Logger.error('startup logger error')");
        execute(engine, context,
                "MinecraftLogger.info('minecraft logger info'); " +
                        "MinecraftLogger.warn('minecraft logger value={}', 2); " +
                        "MinecraftLogger.error('minecraft logger error')");
        testGeneratedMessageApi(engine, context);
        System.out.println("Startup documentation logger tests passed.");
    }

    private static void testGeneratedMessageApi(JexlEngine engine, JexlContext context) {
        GeneratedMessage.reset();
        String configuredStructure = CrashAssistantConfig.get("generated_message.message_structure", false);

        ScriptUtils.setCurrentScriptName("startup/first.jexl");
        assertEquals(configuredStructure, execute(engine, context, "GeneratedMessage.getCurrentStructure()"));
        execute(engine, context,
                "GeneratedMessage.overrideStructure(GeneratedMessage.getCurrentStructure() + '\\n$CUSTOM/details$')");

        ScriptUtils.setCurrentScriptName("startup/second.jexl");
        assertEquals(configuredStructure + "\n$CUSTOM/details$",
                execute(engine, context, "GeneratedMessage.getCurrentStructure()"));
        execute(engine, context,
                "GeneratedMessage.overrideStructure(GeneratedMessage.getCurrentStructure() + '\\nsecond')");
        assertEquals(configuredStructure + "\n$CUSTOM/details$\nsecond", GeneratedMessage.getCurrentStructure());
        assertEquals(configuredStructure,
                CrashAssistantConfig.get("generated_message.message_structure", false));

        execute(engine, context, "Startup.putCopiedResult('same-id', 'second script', 20)");
        execute(engine, context, "GeneratedMessage.putCustom('details', 'same-id', 'second custom', 20)");
        ScriptUtils.setCurrentScriptName("startup/first.jexl");
        execute(engine, context, "Startup.putCopiedResult('same-id', 'first script', 10)");
        execute(engine, context, "GeneratedMessage.putCustom('details', 'same-id', 'first custom', 10)");
        execute(engine, context, "GeneratedMessage.putCustom('literal', 'value', '$HEADER$')");
        assertEquals(2, GeneratedMessage.getAnalysisResults().size());

        Map<String, String> values = new LinkedHashMap<>();
        values.put("HEADER", "Header");
        assertEquals("Before\nsecond custom\nfirst custom\nAfter",
                GeneratedMessage.renderStructure("Before\n$CUSTOM/details$\nAfter", values));
        assertEquals("Before\nAfter",
                GeneratedMessage.renderStructure("Before\n$CUSTOM/missing$\nAfter", values));
        assertEquals("Before:  after",
                GeneratedMessage.renderStructure("Before: $CUSTOM/missing$ after", values));
        assertEquals("Header|$HEADER$",
                GeneratedMessage.renderStructure("$HEADER$|$CUSTOM/literal$", values));

        String serialized = GeneratedMessage.exportState();
        GeneratedMessage.reset();
        GeneratedMessage.importState(serialized);
        assertEquals(configuredStructure + "\n$CUSTOM/details$\nsecond", GeneratedMessage.getCurrentStructure());

        ScriptUtils.setCurrentScriptName("startup/second.jexl");
        execute(engine, context, "GeneratedMessage.clearStructureOverride()");
        assertEquals(configuredStructure + "\n$CUSTOM/details$", GeneratedMessage.getCurrentStructure());
        ScriptUtils.setCurrentScriptName(null);
        GeneratedMessage.reset();
    }

    private static Object execute(JexlEngine engine, JexlContext context, String script) {
        return engine.createScript(script).execute(context);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
