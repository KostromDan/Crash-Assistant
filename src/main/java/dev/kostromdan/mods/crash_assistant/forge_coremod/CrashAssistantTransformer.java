package dev.kostromdan.mods.crash_assistant.forge_coremod;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrashAssistantTransformer implements IClassTransformer, Opcodes {
    public static Logger LOGGER = LogManager.getLogger("CrashAssistantTransformer");

    private static final List<String> TRANSFORM_CLASSES = Arrays.asList(
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.gui.GuiErrorScreen",
            "net.minecraft.client.gui.GuiMainMenu"
    );

    // Method mappings for Minecraft class
    private static final Map<String, String> SHUTDOWN_METHOD = new HashMap<>();
    // Method mappings for GuiErrorScreen class
    private static final Map<String, String> INIT_GUI_METHOD = new HashMap<>();
    // Method mappings for GuiMainMenu class
    private static final Map<String, String> DRAW_SCREEN_METHOD = new HashMap<>();

    static {
        // Initialize Minecraft shutdown method mappings
        SHUTDOWN_METHOD.put("shutdown", "()V");
        SHUTDOWN_METHOD.put("n", "()V");

        // Initialize GuiErrorScreen initGui method mappings
        INIT_GUI_METHOD.put("initGui", "()V");
        INIT_GUI_METHOD.put("b", "()V");

        // Initialize GuiMainMenu drawScreen method mappings
        DRAW_SCREEN_METHOD.put("drawScreen", "(IIF)V");
        DRAW_SCREEN_METHOD.put("a", "(IIF)V");
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TRANSFORM_CLASSES.contains(transformedName)) {
            return basicClass;
        }
        try {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(basicClass);
            classReader.accept(classNode, 0);

            if (transformedName.equals("net.minecraft.client.Minecraft")) {
                transformMinecraft(classNode);
            } else if (transformedName.equals("net.minecraft.client.gui.GuiErrorScreen")) {
                transformGuiErrorScreen(classNode);
            } else if (transformedName.equals("net.minecraft.client.gui.GuiMainMenu")) {
                transformGuiMainMenu(classNode);
            }

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Error while transforming class {}", transformedName, e);
            return basicClass;
        }
    }

    private void transformMinecraft(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<init>")) {
                InsnList toInsert = new InsnList();
                toInsert.add(new MethodInsnNode(INVOKESTATIC, "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks", "afterMinecraftInit", "()V", false));

                AbstractInsnNode returnInsn = null;
                for (AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
                    if (insn.getOpcode() == RETURN) {
                        returnInsn = insn;
                        break;
                    }
                }

                if (returnInsn != null) {
                    method.instructions.insertBefore(returnInsn, toInsert);
                }
            } 
            else if (SHUTDOWN_METHOD.containsKey(method.name) &&
                    (method.desc.equals(SHUTDOWN_METHOD.get(method.name)) || SHUTDOWN_METHOD.get(method.name).isEmpty())) {
                InsnList toInsert = new InsnList();
                toInsert.add(new MethodInsnNode(INVOKESTATIC, "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks", "onMinecraftShutdown", "()V", false));

                AbstractInsnNode returnInsn = null;
                for (AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
                    if (insn.getOpcode() == RETURN) {
                        returnInsn = insn;
                        break;
                    }
                }

                if (returnInsn != null) {
                    method.instructions.insertBefore(returnInsn, toInsert);
                }
            }
        }
    }

    private void transformGuiErrorScreen(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (INIT_GUI_METHOD.containsKey(method.name) &&
                    (method.desc.equals(INIT_GUI_METHOD.get(method.name)) || INIT_GUI_METHOD.get(method.name).isEmpty())) {
                InsnList toInsert = new InsnList();
                toInsert.add(new MethodInsnNode(INVOKESTATIC, "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks", "onErrorScreenInit", "()V", false));

                AbstractInsnNode returnInsn = null;
                for (AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
                    if (insn.getOpcode() == RETURN) {
                        returnInsn = insn;
                        break;
                    }
                }

                if (returnInsn != null) {
                    method.instructions.insertBefore(returnInsn, toInsert);
                }
            }
        }
    }

    private void transformGuiMainMenu(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (DRAW_SCREEN_METHOD.containsKey(method.name) &&
                    (method.desc.equals(DRAW_SCREEN_METHOD.get(method.name)) || DRAW_SCREEN_METHOD.get(method.name).isEmpty())) {
                InsnList toInsert = new InsnList();
                toInsert.add(new MethodInsnNode(INVOKESTATIC, "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks", "onClientLoaded", "()V", false));

                AbstractInsnNode returnInsn = null;
                for (AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
                    if (insn.getOpcode() == RETURN) {
                        returnInsn = insn;
                        break;
                    }
                }

                if (returnInsn != null) {
                    method.instructions.insertBefore(returnInsn, toInsert);
                }
            }
        }
    }
}
