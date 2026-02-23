package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Locale;

import com.sun.management.OperatingSystemMXBean;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

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
     * Returns total swap space (pagefile) size in bytes.
     * On Windows, Java returns Commit Limit (RAM + Pagefile), so we calculate the actual Pagefile size.
     *
     * @return total swap space size in bytes
     */
    public static long getSystemTotalSwapBytes() {
        long rawSwap = OS_BEAN.getTotalSwapSpaceSize();

        if (PlatformHelp.isWindows()) {
            long totalRam = OS_BEAN.getTotalPhysicalMemorySize();
            return Math.max(0L, rawSwap - totalRam);
        }

        return rawSwap;
    }

    /**
     * Returns used swap space (pagefile) size in bytes.
     * On Windows, Java returns Total Committed Memory, so we subtract used physical RAM to get used Pagefile.
     *
     * @return used swap space size in bytes
     */
    public static long getSystemUsedSwapBytes() {
        long rawTotalSwap = OS_BEAN.getTotalSwapSpaceSize();
        long rawFreeSwap = OS_BEAN.getFreeSwapSpaceSize();

        if (rawTotalSwap == 0) {
            return 0L;
        }

        long usedCommitCharge = rawTotalSwap - rawFreeSwap;

        if (PlatformHelp.isWindows()) {
            long totalRam = OS_BEAN.getTotalPhysicalMemorySize();
            long freeRam = OS_BEAN.getFreePhysicalMemorySize();
            long usedRam = totalRam - freeRam;

            return Math.max(0L, usedCommitCharge - usedRam);
        }

        return usedCommitCharge;
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
}