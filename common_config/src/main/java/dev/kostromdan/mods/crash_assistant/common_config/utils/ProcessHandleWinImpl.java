package dev.kostromdan.mods.crash_assistant.common_config.utils;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/* ───────────────────────────────────────────────────────────── */

public class ProcessHandleWinImpl extends ProcessHandleAbstractImpl {

    /* =====  Win32 constants  ===== */
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000; // Vista+
    private static final int PROCESS_TERMINATE = 0x0001;
    private static final int SYNCHRONIZE = 0x0010_0000;
    private static final int WAIT_TIMEOUT = 0x00000102;

    /* =====  FILETIME replacement (adds getFieldOrder)  ===== */
    public static class FILETIMEX extends Structure {
        public int dwLowDateTime;
        public int dwHighDateTime;

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwLowDateTime", "dwHighDateTime");
        }

        public static class ByReference extends FILETIMEX implements Structure.ByReference {
        }

        boolean isZero() {
            return dwLowDateTime == 0 && dwHighDateTime == 0;
        }
    }

    /* =====  PROCESSENTRY32 replacement (adds getFieldOrder)  ===== */
    public static class PROCESSENTRY32Fix extends Tlhelp32.PROCESSENTRY32.ByReference {
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("dwSize", "cntUsage", "th32ProcessID", "th32DefaultHeapID",
                    "th32ModuleID", "cntThreads", "th32ParentProcessID",
                    "pcPriClassBase", "dwFlags", "szExeFile");
        }

        public PROCESSENTRY32Fix() {
            super();
            this.dwSize = new WinDef.DWORD(size());
        }
    }

    /* =====  Kernel32 extension: APIs missing in platform-3.4.0  ===== */
    private interface Kernel32Ext extends Kernel32 {
        Kernel32Ext INSTANCE =
                (Kernel32Ext) Native.loadLibrary("kernel32", Kernel32Ext.class);

        boolean QueryFullProcessImageNameW(WinNT.HANDLE hProcess, int flags,
                                           char[] exe, IntByReference size);

        boolean GetProcessTimes(WinNT.HANDLE hProcess,
                                FILETIMEX create, FILETIMEX exit,
                                FILETIMEX kernel, FILETIMEX user);
    }

    private static final Kernel32Ext K32 = Kernel32Ext.INSTANCE;

    /* =====  small helpers  ===== */
    private static WinNT.HANDLE openProcess(long pid, int access) {
        return Kernel32.INSTANCE.OpenProcess(access, false, (int) pid);
    }

    private static void closeHandle(WinNT.HANDLE h) {
        if (h != null && !WinBase.INVALID_HANDLE_VALUE.equals(h))
            Kernel32.INSTANCE.CloseHandle(h);
    }

    private static long toEpochMillis(FILETIMEX ft) {
        long v = ((long) ft.dwHighDateTime << 32) | (ft.dwLowDateTime & 0xFFFFFFFFL);
        return v / 10_000L - 11_644_473_600_000L;   // 1601-01-01 ➜ 1970-01-01
    }

    /* ─────────────────────────────────────────────────────────────
     *  ProcessHandleAbstractImpl overrides
     * ──────────────────────────────────────────────────────────── */

    @Override
    public long getCurrentProcessId() {
        return Kernel32.INSTANCE.GetCurrentProcessId();
    }

    @Override
    public Optional<String> getCurrentProcessCommand() {
        WinNT.HANDLE self = Kernel32.INSTANCE.GetCurrentProcess();
        char[] buf = new char[WinDef.MAX_PATH * 2];
        IntByReference len = new IntByReference(buf.length);
        return K32.QueryFullProcessImageNameW(self, 0, buf, len)
                ? Optional.of(Native.toString(buf))
                : Optional.empty();
    }

    @Override
    public long getCurrentProcessStartTime() {
        return getProcessStartTime(getCurrentProcessId());
    }

    /**
     * Return the creation time while the process is alive;
     * return -1 if the process has already terminated *or* cannot be queried.
     */
    @Override
    public long getProcessStartTime(long pid) {
        WinNT.HANDLE h = openProcess(pid, PROCESS_QUERY_LIMITED_INFORMATION | SYNCHRONIZE);
        if (h == null) return -1;

        try {
            /* First probe liveness exactly like the JDK: */
            int wait = Kernel32.INSTANCE.WaitForSingleObject(h, 0);
            if (wait != WAIT_TIMEOUT)             // WAIT_TIMEOUT ⇒ still running
                return -1;                        // already dead

            /* Still running ⇒ fetch creation time. */
            FILETIMEX create = new FILETIMEX();
            boolean ok = K32.GetProcessTimes(h, create,
                    new FILETIMEX(), new FILETIMEX(), new FILETIMEX());
            return ok ? toEpochMillis(create) : -1;
        } finally {
            closeHandle(h);
        }
    }

    @Override
    public boolean isProcessAlive(long pid) {
        WinNT.HANDLE h = openProcess(pid, SYNCHRONIZE);
        if (h == null) return false;
        try {
            return Kernel32.INSTANCE.WaitForSingleObject(h, 0) == WAIT_TIMEOUT;
        } finally {
            closeHandle(h);
        }
    }

    @Override
    public String getChildProcessesInfo() {
        long parent = getCurrentProcessId();
        WinNT.HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (WinBase.INVALID_HANDLE_VALUE.equals(snap)) return "";

        List<String> rows = new ArrayList<>();
        try {
            PROCESSENTRY32Fix e = new PROCESSENTRY32Fix();
            if (Kernel32.INSTANCE.Process32First(snap, e)) {
                do {
                    if (e.th32ParentProcessID.longValue() == parent) {
                        long child = e.th32ProcessID.longValue();
                        long start = getProcessStartTime(child);   // returns -1 if not alive
                        if (start != -1) rows.add(child + ": " + start);
                    }
                } while (Kernel32.INSTANCE.Process32Next(snap, e));
            }
        } finally {
            closeHandle(snap);
        }
        return String.join("\n", rows);
    }

    @Override
    public boolean destroyProcess(long pid) {
        return terminate(pid);
    }

    @Override
    public boolean destroyProcessForcibly(long pid) {
        return terminate(pid);
    }

    /* =====  internal terminate helper  ===== */
    private static boolean terminate(long pid) {
        WinNT.HANDLE h = openProcess(pid, PROCESS_TERMINATE);
        if (h == null) return false;
        try {
            return Kernel32.INSTANCE.TerminateProcess(h, 1);
        } // JDK uses exit-code 1
        finally {
            closeHandle(h);
        }
    }
}
