package dev.kostromdan.mods.crash_assistant.forge_coremod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class CrashAssistantTransformer implements IClassTransformer, Opcodes {

    public static final Logger LOGGER = LogManager.getLogger("CrashAssistantTransformer");

    private static final List<String> TRANSFORM_CLASSES = Arrays.asList(
        "net.minecraft.client.Minecraft",
        "net.minecraft.client.gui.GuiErrorScreen",
        "net.minecraft.client.gui.GuiMainMenu");

    // Method mappings for Minecraft class
    private static final Map<String, String> SHUTDOWN_METHOD = new HashMap<>();
    // Method mappings for GuiErrorScreen class
    private static final Map<String, String> INIT_GUI_METHOD = new HashMap<>();
    // Method mappings for GuiMainMenu class
    private static final Map<String, String> DRAW_SCREEN_METHOD = new HashMap<>();

    static {
        // Initialize Minecraft shutdown method mappings
        SHUTDOWN_METHOD.put("shutdown", "()V");
        SHUTDOWN_METHOD.put("k", "()V");

        // Initialize GuiErrorScreen initGui method mappings
        INIT_GUI_METHOD.put("initGui", "()V");
        INIT_GUI_METHOD.put("b", "()V");

        // Initialize GuiMainMenu drawScreen method mappings
        DRAW_SCREEN_METHOD.put("drawScreen", "(IIF)V");
        DRAW_SCREEN_METHOD.put("a", "(IIF)V");
    }

    // ---------------------------------------------------------------------

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TRANSFORM_CLASSES.contains(transformedName)) {
            return basicClass;
        }
        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, ClassReader.EXPAND_FRAMES);
            // keep existing frames

            switch (transformedName) {
                case "net.minecraft.client.Minecraft":
                    transformMinecraft(classNode);
                    break;
                case "net.minecraft.client.gui.GuiErrorScreen":
                    transformGuiErrorScreen(classNode);
                    break;
                case "net.minecraft.client.gui.GuiMainMenu":
                    transformGuiMainMenu(classNode);
                    break;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS); // no COMPUTE_FRAMES
            classNode.accept(cw);
            return cw.toByteArray();

        } catch (Exception ex) {
            LOGGER.error("Error while transforming class {}", transformedName, ex);
            return basicClass; // fall back to original byte‑code
        }
    }

    // ---------------------------------------------------------------------
    // Patching helpers
    // ---------------------------------------------------------------------

    private void transformMinecraft(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals("<init>")) {
                injectBeforeReturn(
                    m,
                    "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks",
                    "afterMinecraftInit",
                    "()V");
            } else if (SHUTDOWN_METHOD.containsKey(m.name) && m.desc.equals(SHUTDOWN_METHOD.get(m.name))) {
                injectBeforeReturn(
                    m,
                    "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks",
                    "onMinecraftShutdown",
                    "()V");
            }
        }
    }

    private void transformGuiErrorScreen(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (INIT_GUI_METHOD.containsKey(m.name) && m.desc.equals(INIT_GUI_METHOD.get(m.name))) {
                injectBeforeReturn(
                    m,
                    "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks",
                    "onErrorScreenInit",
                    "()V");
            }
        }
    }

    private void transformGuiMainMenu(ClassNode cn) {
        for (MethodNode m : cn.methods) {
            if (DRAW_SCREEN_METHOD.containsKey(m.name) && m.desc.equals(DRAW_SCREEN_METHOD.get(m.name))) {
                injectBeforeReturn(
                    m,
                    "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks",
                    "onClientLoaded",
                    "()V");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Utility: inject static call just before the method’s RETURN
    // ---------------------------------------------------------------------
    private static void injectBeforeReturn(MethodNode method, String owner, String name, String desc) {

        InsnList call = new InsnList();
        call.add(new MethodInsnNode(INVOKESTATIC, owner, name, desc, false));

        for (AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
            if (insn.getOpcode() == RETURN) {
                method.instructions.insertBefore(insn, call);
                break;
            }
        }
    }
}
