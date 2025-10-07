package dev.kostromdan.mods.crash_assistant.core_mod.services;

import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CrashAssistantTransformationService implements ITransformationService {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantTransformationService");

    private static String earlyLaunchTarget = "unknown";
    private static String earlyMinecraftVersion = "unknown";

    public CrashAssistantTransformationService() {
        try {
            URL selfUrl = CrashAssistantTransformationService.class.getProtectionDomain().getCodeSource().getLocation();

            ClassLoader classLoader = Launcher.class.getClassLoader();

            if (classLoader instanceof URLClassLoader) {
                Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addUrlMethod.setAccessible(true);
                addUrlMethod.invoke(classLoader, selfUrl);
            } else {
                LOGGER.warn("ClassLoader is not a URLClassLoader. Cannot inject self. This might be okay if it's already on the classpath.");
            }
        } catch (Exception e) {
            LOGGER.error("A critical error occurred while attempting to inject self into classpath.", e);
        }

        try {
            reflectivelyExtractLaunchData();
            PlatformHelp.platform = PlatformHelp.FORGE;
            PlatformHelp.minecraftVersion = earlyMinecraftVersion;
            LibrariesJarLocator.setupLoaderJarName(FMLLoader.class);
            JarInJarHelper.launchCrashAssistantApp(earlyLaunchTarget);
            JarInJarHelper.checkDuplicatedCrashAssistantMod(true);
        } catch (Throwable throwable) {
            LOGGER.error("A critical error occurred during Crash Assistant setup: ", throwable);
        }
    }

    private static void reflectivelyExtractLaunchData() {
        try {
            Field argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
            argumentHandlerField.setAccessible(true);
            Object argumentHandler = argumentHandlerField.get(Launcher.INSTANCE);
            Field argsField = ArgumentHandler.class.getDeclaredField("args");
            argsField.setAccessible(true);
            String[] rawArgs = (String[]) argsField.get(argumentHandler);
            if (rawArgs == null) return;
            for (int i = 0; i < rawArgs.length - 1; i++) {
                if ("--launchTarget".equals(rawArgs[i])) earlyLaunchTarget = rawArgs[i + 1];
                if ("--fml.mcVersion".equals(rawArgs[i])) earlyMinecraftVersion = rawArgs[i + 1];
            }
        } catch (Exception e) {
            LOGGER.error("Failed to reflectively access ModLauncher arguments.", e);
        }
    }

    @Override
    public @NotNull String name() {
        return "crash_assistant";
    }

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void beginScanning(IEnvironment iEnvironment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return Arrays.asList(new InnerCrashAssistantTransformer());
    }

    // FIX: Changed from 'private' to 'public' to resolve IllegalAccessError
    public static class InnerCrashAssistantTransformer implements ITransformer<ClassNode>, org.objectweb.asm.Opcodes {
        private static final String MINECRAFT_CLASS = "net.minecraft.client.Minecraft";

        @Override
        public @NotNull ClassNode transform(ClassNode input, cpw.mods.modlauncher.api.ITransformerVotingContext context) {
            if (input.name.replace('/', '.').equals(MINECRAFT_CLASS)) {
                transformMinecraft(input);
            }
            return input;
        }

        private void transformMinecraft(ClassNode cn) {
            final Map<String, String> SHUTDOWN_METHOD = new HashMap<>();
            SHUTDOWN_METHOD.put("shutdown", "()V");
            SHUTDOWN_METHOD.put("func_71400_g", "()V");

            for (MethodNode m : cn.methods) {
                if (SHUTDOWN_METHOD.containsKey(m.name) && m.desc.equals(SHUTDOWN_METHOD.get(m.name))) {
                    injectBeforeReturn(m, "dev/kostromdan/mods/crash_assistant/common/CrashAssistantHooks", "onMinecraftShutdown", "()V");
                }
            }
        }

        private void injectBeforeReturn(MethodNode method, String owner, String name, String desc) {
            InsnList call = new InsnList();
            call.add(new MethodInsnNode(INVOKESTATIC, owner, name, desc, false));
            for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
                if (insn.getOpcode() == RETURN) {
                    method.instructions.insertBefore(insn, call);
                    break;
                }
            }
        }

        @Override
        public @NotNull Set<Target> targets() {
            Set<Target> targets = new HashSet<>();
            targets.add(Target.targetClass(MINECRAFT_CLASS));
            return targets;
        }

        @Override
        public cpw.mods.modlauncher.api.TransformerVoteResult castVote(cpw.mods.modlauncher.api.ITransformerVotingContext context) {
            return cpw.mods.modlauncher.api.TransformerVoteResult.YES;
        }
    }
}