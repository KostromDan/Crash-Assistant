package dev.kostromdan.mods.crash_assistant.core_mod.services;

import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.ArgUtils;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.LibrariesJarLocator;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import dev.kostromdan.mods.crash_assistant.core_mod.utils.IModLocatorInjector;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CrashAssistantApp should be launched as soon as possible after game start
 * to be able to help players even with coremod/mixin/hs_err crashes.
 * So we launch it from the constructor of the ITransformationService, the first point, we can launch it from the forge.
 */
public class CrashAssistantTransformationService implements ITransformationService {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantTransformationService");

    private static String earlyLaunchTarget = "unknown";
    private static String earlyMinecraftVersion = "unknown";

    public CrashAssistantTransformationService() {
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

    /**
     * Uses reflection to access ModLauncher's ArgumentHandler and its raw command-line arguments
     * before the environment is fully initialized. This is necessary to get critical information
     * like the launch target and game version at the earliest possible moment.
     */
    private static void reflectivelyExtractLaunchData() {
        try {
            Field argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
            argumentHandlerField.setAccessible(true);
            Object argumentHandler = argumentHandlerField.get(Launcher.INSTANCE);

            Field argsField = ArgumentHandler.class.getDeclaredField("args");
            argsField.setAccessible(true);
            String[] rawArgs = (String[]) argsField.get(argumentHandler);

            ArgUtils.setLaunchArgs(rawArgs);

            if (rawArgs == null) {
                LOGGER.warn("Could not find raw launch arguments via reflection; they were null.");
                return;
            }

            for (int i = 0; i < rawArgs.length - 1; i++) {
                if ("--launchTarget".equals(rawArgs[i])) {
                    earlyLaunchTarget = rawArgs[i + 1];
                }
                if ("--fml.mcVersion".equals(rawArgs[i])) {
                    earlyMinecraftVersion = rawArgs[i + 1];
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            LOGGER.error("Failed to reflectively access ModLauncher arguments. This might happen with a future ModLauncher update.", e);
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
    public void onLoad(IEnvironment env, Set<String> otherServices){
        IModLocatorInjector.inject();
    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        List<ITransformer> list = new ArrayList<>();
        list.add(new InnerCrashAssistantTransformer());
        return list;
    }

    private static class InnerCrashAssistantTransformer implements ITransformer<org.objectweb.asm.tree.ClassNode>, org.objectweb.asm.Opcodes {
        private static final java.util.List<String> TRANSFORM_CLASSES = java.util.Arrays.asList(
                "net.minecraft.client.Minecraft"
        );
        private static final java.util.Map<String, String> SHUTDOWN_METHOD = new java.util.HashMap<>();
        static {
            SHUTDOWN_METHOD.put("shutdown", "()V");
            SHUTDOWN_METHOD.put("func_71400_g", "()V");
        }
        @Override
        public org.objectweb.asm.tree.ClassNode transform(org.objectweb.asm.tree.ClassNode input, cpw.mods.modlauncher.api.ITransformerVotingContext context) {
            try {
                String transformedName = input.name.replace('/', '.');
                if (!TRANSFORM_CLASSES.contains(transformedName)) {
                    return input;
                }
                switch (transformedName) {
                    case "net.minecraft.client.Minecraft":
                        transformMinecraft(input);
                        break;
                }
            } catch (Throwable t) { }
            return input;
        }
        private void transformMinecraft(org.objectweb.asm.tree.ClassNode cn) {
            for (org.objectweb.asm.tree.MethodNode m : cn.methods) {
                if (SHUTDOWN_METHOD.containsKey(m.name) && m.desc.equals(SHUTDOWN_METHOD.get(m.name))) {
                    injectBeforeReturn(m, "dev/kostromdan/mods/crash_assistant/common/events/CrashAssistantEvents", "onMinecraftShutdown", "()V");
                }
            }
        }
        private static void injectBeforeReturn(org.objectweb.asm.tree.MethodNode method, String owner, String name, String desc) {
            org.objectweb.asm.tree.InsnList call = new org.objectweb.asm.tree.InsnList();
            call.add(new org.objectweb.asm.tree.MethodInsnNode(INVOKESTATIC, owner, name, desc, false));
            for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
                if (insn.getOpcode() == RETURN) {
                    method.instructions.insertBefore(insn, call);
                    break;
                }
            }
        }
        @Override
        public java.util.Set<Target> targets() {
            java.util.Set<Target> set = new java.util.HashSet<>();
            set.add(Target.targetClass("net.minecraft.client.Minecraft"));
            return set;
        }
        @Override
        public cpw.mods.modlauncher.api.TransformerVoteResult castVote(cpw.mods.modlauncher.api.ITransformerVotingContext context) {
            return cpw.mods.modlauncher.api.TransformerVoteResult.YES;
        }
    }
}
