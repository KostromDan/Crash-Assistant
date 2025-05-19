package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * DirectX GPU detector implemented with the Foreign-Function & Memory API (Java 22+).
 * <p>
 * The native DLL that exports
 * <code>char* DetectGPUsFFM(char* buf, size_t size)</code>
 * must already be loaded into the current process (for example via {@code System.loadLibrary}).
 * <p>
 * The returned string uses the same serialization format as the original JNI detector.
 */
public final class DirectXGPUDetectorFFM {
    /**
     * Native symbol exported by the DLL.
     */
    private static final String SYMBOL_DETECT_GPUS = "DetectGPUsFFM";

    /**
     * Temporary buffer size; must match the constant in the C code.
     */
    private static final int BUFFER_SIZE = 4096;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOADER_LOOKUP = SymbolLookup.loaderLookup();

    private DirectXGPUDetectorFFM() {
    }

    /**
     * Invokes the native <code>DetectGPUsFFM</code> function via FFM and returns the
     * serialized GPU list.
     *
     * @throws UnsatisfiedLinkError if the symbol is not found
     * @throws RuntimeException     if any error occurs while calling the native code
     */
    public static String getSerialisedGPUs() {
        // 1) look up the native symbol in the already-loaded libraries
        var symbol = LOADER_LOOKUP.find(SYMBOL_DETECT_GPUS)
                .orElseThrow(() ->
                        new UnsatisfiedLinkError("Symbol " + SYMBOL_DETECT_GPUS + " not found"));

        // 2) describe the signature: char* DetectGPUsFFM(char* buf, size_t size)
        FunctionDescriptor desc = FunctionDescriptor.of(
                ValueLayout.ADDRESS,      // return: char*
                ValueLayout.ADDRESS,      // arg0: buffer pointer
                ValueLayout.JAVA_LONG     // arg1: size_t length
        );
        MethodHandle handle = LINKER.downcallHandle(symbol, desc);

        // 3) allocate a temporary buffer and call into native code
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(BUFFER_SIZE, 1);
            handle.invoke(buffer, (long) BUFFER_SIZE);

            // 4) manually decode a NUL-terminated UTF-8 string
            ByteBuffer bb = buffer.asByteBuffer();
            byte[] data = new byte[BUFFER_SIZE];
            bb.get(data);

            int len = 0;
            while (len < data.length && data[len] != 0) {
                len++;
            }
            return new String(data, 0, len, StandardCharsets.UTF_8);

        } catch (Throwable t) {
            throw new RuntimeException("FFM GPU detection failed", t);
        }
    }
}
