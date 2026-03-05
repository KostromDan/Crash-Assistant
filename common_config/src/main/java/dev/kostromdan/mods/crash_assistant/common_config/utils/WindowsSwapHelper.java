package dev.kostromdan.mods.crash_assistant.common_config.utils;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * Internal helper class for directly reading exact pagefile data on Windows.
 * Isolated to prevent NoClassDefFoundError on Linux/macOS environments.
 */
public final class WindowsSwapHelper {
    public interface Ntdll extends Library {
        Ntdll INSTANCE = (Ntdll) Native.loadLibrary("Ntdll", Ntdll.class);

        int NtQuerySystemInformation(int SystemInformationClass, Pointer SystemInformation, int SystemInformationLength, IntByReference ReturnLength);
    }

    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = (Kernel32) Native.loadLibrary("Kernel32", Kernel32.class);

        void GetSystemInfo(Pointer lpSystemInfo);
    }

    private static volatile long cachedTotalSwap = 0;
    private static volatile long cachedUsedSwap = 0;
    private static volatile long lastUpdate = 0;
    private static volatile boolean jnaSupported = true;

    public static synchronized void update() {
        if (!jnaSupported) return;
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 1000) return; // Cache the result for 1 second

        try {
            // 1. Get system page size (usually 4096 bytes)
            // dwPageSize is at offset 4 in SYSTEM_INFO
            Memory sysInfo = new Memory(64);
            Kernel32.INSTANCE.GetSystemInfo(sysInfo);
            long pageSize = sysInfo.getInt(4) & 0xFFFFFFFFL;

            // 2. Query SystemPageFileInformation (Class 18)
            Memory buffer = new Memory(8192); // 8KB is enough for the pagefile list
            IntByReference retLen = new IntByReference();
            int status = Ntdll.INSTANCE.NtQuerySystemInformation(18, buffer, (int) buffer.size(), retLen);

            // STATUS_SUCCESS == 0
            if (status == 0) {
                long totalPages = 0;
                long usedPages = 0;
                int offset = 0;

                // Parse the SYSTEM_PAGEFILE_INFORMATION structure manually (safe for JNA 3.4.0)
                while (offset + 12 <= buffer.size()) {
                    int nextOffset = buffer.getInt(offset);
                    int totalSize = buffer.getInt(offset + 4);
                    int totalInUse = buffer.getInt(offset + 8);

                    totalPages += (totalSize & 0xFFFFFFFFL);
                    usedPages += (totalInUse & 0xFFFFFFFFL);

                    if (nextOffset == 0) break;
                    offset += nextOffset;
                }

                // Convert pages to actual bytes
                cachedTotalSwap = totalPages * pageSize;
                cachedUsedSwap = usedPages * pageSize;
            } else {
                cachedTotalSwap = 0;
                cachedUsedSwap = 0;
            }
        } catch (Throwable t) {
            jnaSupported = false; // Disable JNA on fatal library load error
        }
        lastUpdate = now;
    }

    public static boolean isSupported() {
        if (lastUpdate == 0) update();
        return jnaSupported;
    }

    public static long getTotalSwap() {
        update();
        return cachedTotalSwap;
    }

    public static long getUsedSwap() {
        update();
        return cachedUsedSwap;
    }
}