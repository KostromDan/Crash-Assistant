package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import com.sun.management.OperatingSystemMXBean;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import org.apache.commons.jexl3.annotations.NoJexl;

public final class MemoryUtils {
    public static final long BYTES_IN_MEGABYTE = 1024L * 1024L;
    public static final long BYTES_IN_GIGABYTE = 1024L * 1024L * 1024L;

    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean OS_BEAN = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    /**
     * Utility class, do not instantiate.
     */
    private MemoryUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns initial JVM heap size (Xms) in bytes.
     *
     * @return initial JVM heap size in bytes
     */
    public static long getJvmInitialHeapBytes() {
        return MEMORY_BEAN.getHeapMemoryUsage().getInit();
    }

    /**
     * Returns max JVM heap size (Xmx) in bytes.
     *
     * @return max JVM heap size in bytes, or 0 if undefined
     */
    public static long getJvmMaxHeapBytes() {
        long max = MEMORY_BEAN.getHeapMemoryUsage().getMax();
        return max < 0 ? 0L : max;
    }

    /**
     * Returns the amount of memory currently allocated/committed by the JVM from the OS in bytes.
     *
     * @return allocated memory in bytes
     */
    public static long getJvmAllocatedMemoryBytes() {
        return MEMORY_BEAN.getHeapMemoryUsage().getCommitted();
    }

    /**
     * Returns total physical RAM in the system in bytes.
     *
     * @return total physical RAM in bytes
     */
    public static long getSystemTotalMemoryBytes() {
        return OS_BEAN.getTotalPhysicalMemorySize();
    }

    /**
     * Returns used physical RAM in the system in bytes.
     *
     * @return used physical RAM in bytes
     */
    public static long getSystemUsedMemoryBytes() {
        long totalMemory = OS_BEAN.getTotalPhysicalMemorySize();
        long freeMemory = OS_BEAN.getFreePhysicalMemorySize();

        return totalMemory - freeMemory;
    }

    /**
     * Returns free physical RAM in the system in bytes.
     *
     * @return free physical RAM in bytes
     */
    public static long getSystemFreeMemoryBytes() {
        return OS_BEAN.getFreePhysicalMemorySize();
    }

    /**
     * Returns the root disk of the current working path.
     *
     * @return disk name/path, for example {@code C:\}
     */
    public static String getDiskName() {
        Path currentPath = Paths.get("").toAbsolutePath().normalize();
        Path rootPath = currentPath.getRoot();
        return rootPath != null ? rootPath.toString() : currentPath.toString();
    }

    /**
     * Returns the main class name from {@code sun.java.command}.
     *
     * @return main class name, or {@code UNDEFINED} if unavailable
     */
    public static String getMainClassName() {
        String command = System.getProperty("sun.java.command");
        if (command == null) {
            return "UNDEFINED";
        }

        command = command.trim();
        if (command.isEmpty()) {
            return "UNDEFINED";
        }

        int firstSpace = command.indexOf(' ');
        if (firstSpace < 0) {
            return command;
        }

        return firstSpace == 0 ? "UNDEFINED" : command.substring(0, firstSpace);
    }

    private static Path getCurrentDiskPath() {
        return Paths.get("").toAbsolutePath().normalize();
    }

    /**
     * Returns total disk space of the current path (Paths.get("")) in bytes.
     * This represents the total capacity of the partition.
     *
     * @return total disk space in bytes
     */
    public static long getDiskTotalSpaceBytes() {
        return getCurrentDiskPath().toFile().getTotalSpace();
    }

    /**
     * Returns free disk space of the current path (Paths.get("")) in bytes.
     * This represents the total number of unallocated bytes on the partition.
     *
     * @return free disk space in bytes
     */
    public static long getDiskFreeSpaceBytes() {
        return getCurrentDiskPath().toFile().getFreeSpace();
    }

    /**
     * Returns usable disk space of the current path (Paths.get("")) in bytes.
     * This represents the number of bytes available to this virtual machine on the partition.
     *
     * @return usable disk space in bytes
     */
    public static long getDiskUsableSpaceBytes() {
        return getCurrentDiskPath().toFile().getUsableSpace();
    }

    /**
     * Returns used disk space of the current path (Paths.get("")) in bytes.
     * Calculated as total space minus free space.
     *
     * @return used disk space in bytes
     */
    public static long getDiskUsedSpaceBytes() {
        return getDiskTotalSpaceBytes() - getDiskFreeSpaceBytes();
    }

    /**
     * Returns total swap space (pagefile) size in bytes.
     * On Windows, we use JNA to get the actual Pagefile size.
     * If JNA fails, we fall back to mathematical approximation (Commit Limit - RAM).
     *
     * @return total swap space size in bytes
     */
    public static long getSystemTotalSwapBytes() {
        if (PlatformHelp.isWindows()) {
            if (WindowsSwapHelper.isSupported()) {
                return WindowsSwapHelper.getTotalSwap();
            } else {
                // Fallback logic if JNA fails on Windows
                long rawSwap = OS_BEAN.getTotalSwapSpaceSize();
                long totalRam = OS_BEAN.getTotalPhysicalMemorySize();
                return Math.max(0L, rawSwap - totalRam);
            }
        }

        return OS_BEAN.getTotalSwapSpaceSize();
    }

    /**
     * Returns used swap space (pagefile) size in bytes.
     * On Windows, we use JNA to get the actual Pagefile usage.
     * If JNA fails, we fall back to mathematical approximation (Commit Charge - used RAM)
     * and cap it at total swap to prevent anomalous readings.
     *
     * @return used swap space size in bytes
     */
    public static long getSystemUsedSwapBytes() {
        if (PlatformHelp.isWindows()) {
            if (WindowsSwapHelper.isSupported()) {
                return WindowsSwapHelper.getUsedSwap();
            } else {
                // Fallback logic if JNA fails on Windows
                long rawTotalSwap = OS_BEAN.getTotalSwapSpaceSize();
                long rawFreeSwap = OS_BEAN.getFreeSwapSpaceSize();

                if (rawTotalSwap == 0) return 0L;

                long usedCommitCharge = rawTotalSwap - rawFreeSwap;
                long totalRam = OS_BEAN.getTotalPhysicalMemorySize();
                long freeRam = OS_BEAN.getFreePhysicalMemorySize();
                long usedRam = totalRam - freeRam;

                long calculatedUsedSwap = Math.max(0L, usedCommitCharge - usedRam);
                long totalSwap = getSystemTotalSwapBytes();

                return Math.min(totalSwap, calculatedUsedSwap);
            }
        }

        long rawTotalSwap = OS_BEAN.getTotalSwapSpaceSize();
        long rawFreeSwap = OS_BEAN.getFreeSwapSpaceSize();

        if (rawTotalSwap == 0) {
            return 0L;
        }

        return rawTotalSwap - rawFreeSwap;
    }

    /**
     * Returns free swap space (pagefile) size in bytes.
     *
     * @return free swap space size in bytes
     */
    public static long getSystemFreeSwapBytes() {
        if (PlatformHelp.isWindows()) {
            return Math.max(0L, getSystemTotalSwapBytes() - getSystemUsedSwapBytes());
        }

        return OS_BEAN.getFreeSwapSpaceSize();
    }

    /**
     * Converts bytes to megabytes.
     *
     * @param bytes amount of bytes to convert
     * @return converted value in megabytes
     */
    public static double bytesToMegabytes(long bytes) {
        return (double) bytes / BYTES_IN_MEGABYTE;
    }

    /**
     * Converts bytes to gigabytes.
     *
     * @param bytes amount of bytes to convert
     * @return converted value in gigabytes
     */
    public static double bytesToGigabytes(long bytes) {
        return (double) bytes / BYTES_IN_GIGABYTE;
    }

    /**
     * Formats memory size in bytes to a human-readable format suitable for Xmx/Xms arguments.
     *
     * @param bytes Memory size in bytes
     * @return Formatted memory size (e.g., "512m", "2g", "2.5g")
     */
    public static String formatMemorySize(long bytes) {
        if (bytes >= BYTES_IN_GIGABYTE) {
            double value = bytesToGigabytes(bytes);
            if (value == (long) value) {
                return String.format(Locale.US, "%dg", (long) value);
            }
            return String.format(Locale.US, "%.1fg", value);
        } else {
            double value = bytesToMegabytes(bytes);
            if (value == (long) value) {
                return String.format(Locale.US, "%dm", (long) value);
            }
            return String.format(Locale.US, "%.1fm", value);
        }
    }

    /**
     * Parses a formatted memory size string back into bytes.
     * This is the reverse function for formatMemorySize.
     *
     * @param formattedSize the formatted memory size string (e.g., "512m", "2G", "2.5g")
     * @return memory size in bytes
     * @throws IllegalArgumentException if the format is invalid
     */
    public static long parseMemorySize(String formattedSize) {
        if (formattedSize == null || formattedSize.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory size string cannot be null or empty");
        }

        String normalized = formattedSize.trim().toLowerCase(Locale.US);
        char lastChar = normalized.charAt(normalized.length() - 1);

        try {
            if (Character.isDigit(lastChar)) {
                // No unit suffix, assume raw bytes
                return Long.parseLong(normalized);
            }

            String numberPart = normalized.substring(0, normalized.length() - 1);
            double value = Double.parseDouble(numberPart);

            switch (lastChar) {
                case 'g':
                    return Math.round(value * BYTES_IN_GIGABYTE);
                case 'm':
                    return Math.round(value * BYTES_IN_MEGABYTE);
                case 'k':
                    return Math.round(value * 1024L);
                case 't':
                    return Math.round(value * BYTES_IN_GIGABYTE * 1024L);
                case 'b':
                    return Math.round(value);
                default:
                    throw new IllegalArgumentException("Unknown memory unit: " + lastChar);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid memory size format: " + formattedSize, e);
        }
    }

    @NoJexl
    public static void main(String[] args) {
        System.out.println("JNA Supported (Windows check): " + (PlatformHelp.isWindows() ? WindowsSwapHelper.isSupported() : "N/A (Not Windows)"));
        System.out.println("getMainClassName(): " + getMainClassName());

        System.out.println("--- JVM Memory ---");
        long jvmInit = getJvmInitialHeapBytes();
        System.out.println("getJvmInitialHeapBytes(): " + jvmInit + " bytes (" + formatMemorySize(jvmInit) + ")");

        long jvmMax = getJvmMaxHeapBytes();
        System.out.println("getJvmMaxHeapBytes(): " + jvmMax + " bytes (" + formatMemorySize(jvmMax) + ")");

        long jvmAllocated = getJvmAllocatedMemoryBytes();
        System.out.println("getJvmAllocatedMemoryBytes(): " + jvmAllocated + " bytes (" + formatMemorySize(jvmAllocated) + ")");

        System.out.println("--- System RAM ---");
        long sysTotalMem = getSystemTotalMemoryBytes();
        System.out.println("getSystemTotalMemoryBytes(): " + sysTotalMem + " bytes (" + formatMemorySize(sysTotalMem) + ")");

        long sysUsedMem = getSystemUsedMemoryBytes();
        System.out.println("getSystemUsedMemoryBytes(): " + sysUsedMem + " bytes (" + formatMemorySize(sysUsedMem) + ")");

        long sysFreeMem = getSystemFreeMemoryBytes();
        System.out.println("getSystemFreeMemoryBytes(): " + sysFreeMem + " bytes (" + formatMemorySize(sysFreeMem) + ")");

        System.out.println("--- Disk Space ---");
        String diskName = getDiskName();
        System.out.println("getDiskName(): " + diskName);

        long diskTotal = getDiskTotalSpaceBytes();
        System.out.println("getDiskTotalSpaceBytes(): " + diskTotal + " bytes (" + formatMemorySize(diskTotal) + ")");

        long diskFree = getDiskFreeSpaceBytes();
        System.out.println("getDiskFreeSpaceBytes(): " + diskFree + " bytes (" + formatMemorySize(diskFree) + ")");

        long diskUsable = getDiskUsableSpaceBytes();
        System.out.println("getDiskUsableSpaceBytes(): " + diskUsable + " bytes (" + formatMemorySize(diskUsable) + ")");

        long diskUsed = getDiskUsedSpaceBytes();
        System.out.println("getDiskUsedSpaceBytes(): " + diskUsed + " bytes (" + formatMemorySize(diskUsed) + ")");

        System.out.println("--- Swap/Pagefile ---");
        long sysTotalSwap = getSystemTotalSwapBytes();
        System.out.println("getSystemTotalSwapBytes(): " + sysTotalSwap + " bytes (" + formatMemorySize(sysTotalSwap) + ")");

        long sysUsedSwap = getSystemUsedSwapBytes();
        System.out.println("getSystemUsedSwapBytes(): " + sysUsedSwap + " bytes (" + formatMemorySize(sysUsedSwap) + ")");

        long sysFreeSwap = getSystemFreeSwapBytes();
        System.out.println("getSystemFreeSwapBytes(): " + sysFreeSwap + " bytes (" + formatMemorySize(sysFreeSwap) + ")");

        System.out.println("=== Check finished ===");
    }
}
