package dev.kostromdan.mods.crash_assistant.common_config.scripts;

import dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions.Permissions;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;

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
        System.out.println("Startup documentation logger tests passed.");
    }

    private static Object execute(JexlEngine engine, JexlContext context, String script) {
        return engine.createScript(script).execute(context);
    }
}
