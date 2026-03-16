package dev.kostromdan.mods.crash_assistant.forge_coremod;

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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrashAssistantTransformer implements IClassTransformer, Opcodes {

    public static final Logger LOGGER = LogManager.getLogger("CrashAssistantTransformer");

    private static final List<String> TRANSFORM_CLASSES = Arrays.asList(
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.gui.GuiErrorScreen"
    );

    private static final Map<String, String> SHUTDOWN_METHOD = new HashMap<>();
    private static final Map<String, String> INIT_GUI_METHOD = new HashMap<>();

    static {
        SHUTDOWN_METHOD.put("shutdown", "()V");
        SHUTDOWN_METHOD.put("n", "()V");

        INIT_GUI_METHOD.put("initGui", "()V");
        INIT_GUI_METHOD.put("b", "()V");
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!TRANSFORM_CLASSES.contains(transformedName)) {
            return basicClass;
        }
        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, ClassReader.EXPAND_FRAMES);

            switch (transformedName) {
                case "net.minecraft.client.Minecraft":
                    transformMinecraft(classNode);
                    break;
                case "net.minecraft.client.gui.GuiErrorScreen":
                    transformGuiErrorScreen(classNode);
                    break;
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(cw);
            return cw.toByteArray();

        } catch (Exception ex) {
            LOGGER.error("Error while transforming class {}", transformedName, ex);
            return basicClass;
        }
    }

    @SuppressWarnings("unchecked")
    private void transformMinecraft(ClassNode cn) {
        for (Object methodObj : cn.methods) {
            MethodNode m = (MethodNode) methodObj;
            if (SHUTDOWN_METHOD.containsKey(m.name) && m.desc.equals(SHUTDOWN_METHOD.get(m.name))) {
                injectBeforeReturn(
                        m,
                        "dev/kostromdan/mods/crash_assistant/common/events/CrashAssistantEvents",
                        "onMinecraftShutdown",
                        "()V");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void transformGuiErrorScreen(ClassNode cn) {
        for (Object methodObj : cn.methods) {
            MethodNode m = (MethodNode) methodObj;
            if (INIT_GUI_METHOD.containsKey(m.name) && m.desc.equals(INIT_GUI_METHOD.get(m.name))) {
                injectBeforeReturn(
                        m,
                        "dev/kostromdan/mods/crash_assistant/common/events/CrashAssistantEvents",
                        "onErrorScreenInit",
                        "()V");
            }
        }
    }

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