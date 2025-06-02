package dev.kostromdan.mods.crash_assistant.common_config.utils;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.Tlhelp32.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Windows-specific implementation of {@link ProcessHandleAbstractImpl} using JNA to call
 * Win32 APIs directly. This class matches the observable behavior of the JDK's
 * {@code ProcessHandle} on Windows.
 */
@SuppressWarnings("unused")
public class ProcessHandleWinImpl extends ProcessHandleAbstractImpl {

    /* ====================================================
       Constant definitions
       ==================================================== */

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000; // Vista+
    private static final int PROCESS_TERMINATE                 = 0x0001;
    private static final int SYNCHRONIZE                       = 0x0010_0000; // needed for WaitForSingleObject

    private static final int WAIT_TIMEOUT  = 0x00000102;

    /* ====================================================
       Kernel32 extensions missing from jna‑platform
       ==================================================== */

    private interface Kernel32Ext extends Kernel32 {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean QueryFullProcessImageNameW(WinNT.HANDLE hProcess, int flags, char[] exeName, IntByReference lpdwSize);

        boolean GetProcessTimes(WinNT.HANDLE hProcess,
                                WinBase.FILETIME lpCreationTime,
                                WinBase.FILETIME lpExitTime,
                                WinBase.FILETIME lpKernelTime,
                                WinBase.FILETIME lpUserTime);
    }

    private static final Kernel32Ext K32 = Kernel32Ext.INSTANCE;

    /* ====================================================
       Low‑level helpers
       ==================================================== */

    private static WinNT.HANDLE openProcess(long pid, int access) {
        return Kernel32.INSTANCE.OpenProcess(access, false, (int) pid);
    }

    private static void closeHandle(WinNT.HANDLE h) {
        if (h != null && !WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            Kernel32.INSTANCE.CloseHandle(h);
        }
    }

    private static long fileTimeToEpochMillis(WinBase.FILETIME ft) {
        long high = ((long) ft.dwHighDateTime) << 32;
        long low  = ft.dwLowDateTime & 0xFFFF_FFFFL;
        long fileTime100ns = high | low;
        long msSince1601   = fileTime100ns / 10_000L;
        return msSince1601 - 11_644_473_600_000L; // diff 1601‑01‑01 → 1970‑01‑01
    }

    /* ====================================================
       ProcessHelperImpl overrides
       ==================================================== */

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

    @Override
    public long getProcessStartTime(long pid) {
        WinNT.HANDLE h = openProcess(pid, PROCESS_QUERY_LIMITED_INFORMATION);
        if (h == null) return -1;
        try {
            WinBase.FILETIME ftCreate = new WinBase.FILETIME();
            return K32.GetProcessTimes(h, ftCreate, new WinBase.FILETIME(), new WinBase.FILETIME(), new WinBase.FILETIME())
                    ? fileTimeToEpochMillis(ftCreate)
                    : -1;
        } finally {
            closeHandle(h);
        }
    }

    @Override
    public boolean isProcessAlive(long pid) {
        WinNT.HANDLE h = openProcess(pid, SYNCHRONIZE);
        if (h == null) return false;
        try {
            // probe without waiting – same trick as JDK
            int res = Kernel32.INSTANCE.WaitForSingleObject(h, 0);
            return res == WAIT_TIMEOUT;
        } finally {
            closeHandle(h);
        }
    }

    @Override
    public String getChildProcessesInfo() {
        long parentPid = getCurrentProcessId();
        WinNT.HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
        if (WinBase.INVALID_HANDLE_VALUE.equals(snap)) return "";
        List<String> lines = new ArrayList<>();
        try {
            PROCESSENTRY32.ByReference e = new PROCESSENTRY32.ByReference();
            if (Kernel32.INSTANCE.Process32First(snap, e)) {
                do {
                    if (e.th32ParentProcessID.longValue() == parentPid) {
                        long cpid = e.th32ProcessID.longValue();
                        long start = getProcessStartTime(cpid);
                        if (start != -1) lines.add(cpid + ": " + start);
                    }
                } while (Kernel32.INSTANCE.Process32Next(snap, e));
            }
        } finally {
            closeHandle(snap);
        }
        return String.join("\n", lines);
    }

    @Override
    public boolean destroyProcess(long pid) {
        return terminate(pid);
    }

    @Override
    public boolean destroyProcessForcibly(long pid) {
        // On Windows JDK both variants are identical – keep parity.
        return terminate(pid);
    }

    /* ====================================================
       Internals
       ==================================================== */

    private static boolean terminate(long pid) {
        WinNT.HANDLE h = openProcess(pid, PROCESS_TERMINATE);
        if (h == null) return false;
        try {
            // JDK hard‑codes exit code 1 (see ProcessHandleImpl_win.c)
            return Kernel32.INSTANCE.TerminateProcess(h, 1);
        } finally {
            closeHandle(h);
        }
    }
}
