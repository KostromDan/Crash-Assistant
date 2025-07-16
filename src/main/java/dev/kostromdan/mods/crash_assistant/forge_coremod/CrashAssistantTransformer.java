package dev.kostromdan.mods.crash_assistant.forge_coremod;

import java.util.HashMap;
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

    private static final Logger LOGGER = LogManager.getLogger("CrashAssistantTransformer");

    private static final Map<String, String> SHUTDOWN_METHOD = new HashMap<>();

    static {
        SHUTDOWN_METHOD.put("shutdown", "()V");
        SHUTDOWN_METHOD.put("k", "()V");
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!"net.minecraft.client.Minecraft".equals(transformedName)) {
            return basicClass;
        }

        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, ClassReader.EXPAND_FRAMES);

            for (MethodNode method : classNode.methods) {
                if (SHUTDOWN_METHOD.containsKey(method.name) && method.desc.equals(SHUTDOWN_METHOD.get(method.name))) {

                    InsnList call = new InsnList();
                    call.add(
                        new MethodInsnNode(
                            INVOKESTATIC,
                            "dev/kostromdan/mods/crash_assistant/forge_coremod/CrashAssistantHooks",
                            "onMinecraftShutdown",
                            "()V",
                            false));

                    for (AbstractInsnNode insn = method.instructions.getLast(); insn
                        != null; insn = insn.getPrevious()) {
                        if (insn.getOpcode() == RETURN) {
                            method.instructions.insertBefore(insn, call);
                            break;
                        }
                    }
                }
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(cw);
            return cw.toByteArray();

        } catch (Exception ex) {
            LOGGER.error("Error transforming Minecraft class", ex);
            return basicClass;
        }
    }
}
