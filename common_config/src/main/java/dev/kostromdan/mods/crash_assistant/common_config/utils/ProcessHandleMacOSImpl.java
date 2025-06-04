package dev.kostromdan.mods.crash_assistant.common_config.utils;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Unix/Linux‑specific implementation of {@link ProcessHandleAbstractImpl}
 */
public class ProcessHandleMacOSImpl extends ProcessHandleUnixAbstractImpl {

    @Override
    public Optional<String> getCurrentProcessCommand() {
        long pid = getCurrentProcessId();

        // ─────── macOS: call proc_pidpath(3) via JNA ─────────
        try {
            byte[] buf = new byte[4096];
            int ret = MAC_PROC.proc_pidpath((int) pid, buf, buf.length);
            if (ret > 0) {
                // proc_pidpath always returns a NUL‐terminated C string (even if there are spaces).
                String path = Native.toString(buf);
                if (!path.isEmpty()) {
                    return Optional.of(path);
                }
            }
        } catch (Throwable ignored) {
            // Fall back to ps… in case something goes wrong
        }

        return Optional.empty();
    }

// … elsewhere in the same class: …

    // macOS JNA binding for proc_pidpath(3):
    private interface MacProc extends Library {
        /**
         * int proc_pidpath(int pid, void *buffer, uint32_t buffersize);
         * Returns number of bytes returned in buffer (or 0 on failure).
         */
        int proc_pidpath(int pid, byte[] buffer, int buffersize);
    }

    private static final MacProc MAC_PROC = Native.loadLibrary("proc", MacProc.class);

    @Override
    public long getCurrentProcessStartTime() {
        return getProcessStartTime(getCurrentProcessId());
    }

    @Override
    public long getProcessStartTime(long pid) {
        try {
            int[] mib = new int[]{CTL_KERN, KERN_PROC, KERN_PROC_PID, (int) pid};
            IntByReference sizeRef = new IntByReference(0);
            // First call to get size
            int rc1 = MACC.sysctl(mib, mib.length, null, sizeRef, null, 0);
            int actualSize = sizeRef.getValue();
            if (rc1 != 0 || actualSize < KinfoProc.BYTES) {
                return 0;
            }
            Memory m = new Memory(actualSize);
            m.clear(actualSize);
            IntByReference newSize = new IntByReference(actualSize);
            int rc2 = MACC.sysctl(mib, mib.length, m, newSize, null, 0);
            if (rc2 != 0) {
                return 0;
            }
            KinfoProc kp = new KinfoProc(m);
            kp.read();
            if (kp.p_pid != pid) {
                return 0;
            }
            long ms = kp.tv_sec * 1000L + (kp.tv_usec / 1000L);
            return ms;
        } catch (Throwable ignored) {
            return 0;
        }
    }
    //───────────────────────────────────────────────────────────────────────────
    // macOS “kinfo_proc”‐via‐sysctl portion (no SystemB dependency)
    //───────────────────────────────────────────────────────────────────────────

    private interface MacCLibrary extends Library {
        int sysctl(int[] name, int namelen, Pointer oldp, IntByReference oldlenp, Pointer newp, int newlen);
    }

    private static final MacCLibrary MACC = Native.loadLibrary("c", MacCLibrary.class);

    private static final int CTL_KERN = 1;
    private static final int KERN_PROC = 14;
    private static final int KERN_PROC_PID = 1;

    @SuppressWarnings("unused")
    public static class KinfoProc extends Structure {
        public static final int BYTES = 0x158;

        // dummy placeholder for the first 16 bytes of extern_proc.__p_forw/back
        public byte[] _p_forw_back = new byte[16];
        public int p_pid;           // pid_t (4 bytes)
        public int _pad_pid;        // padding (4 bytes)
        public byte[] _pad_until_timeval = new byte[0x138];
        public long tv_sec;         // time_t (64-bit)
        public int tv_usec;         // suseconds_t (32-bit)
        public int _pad_tv;         // padding to align structure

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "_p_forw_back",
                    "p_pid",
                    "_pad_pid",
                    "_pad_until_timeval",
                    "tv_sec",
                    "tv_usec",
                    "_pad_tv"
            );
        }

        public KinfoProc() {
            super(new Memory(BYTES));
            getPointer().clear(BYTES);
        }

        public KinfoProc(Pointer p) {
            super(p);
        }

        @Override
        public void read() {
            super.read();
        }
    }
}
