package dev.kostromdan.mods.crash_assistant.common_config.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.util.Optional;
import java.util.StringTokenizer;

/**
 * Unix/Linux‑specific implementation of {@link ProcessHandleAbstractImpl}
 */
public class ProcessHandleLinuxImpl extends ProcessHandleUnixAbstractImpl {

    @Override
    public Optional<String> getCurrentProcessCommand() {
        long pid = getCurrentProcessId();

        // ─────── LINUX: read /proc/<pid>/cmdline ─────────
        try {
            java.nio.file.Path cmdlinePath = java.nio.file.Paths.get("/proc/" + pid + "/cmdline");
            byte[] data = java.nio.file.Files.readAllBytes(cmdlinePath);
            if (data.length > 0) {
                // cmdline is “argv[0]\0argv[1]\0...”.  Take up to the first '\0'.
                int end = 0;
                while (end < data.length && data[end] != 0) {
                    end++;
                }
                String firstToken = new String(data, 0, end, java.nio.charset.StandardCharsets.UTF_8);
                if (!firstToken.isEmpty()) {
                    return Optional.of(firstToken);
                }
            }
        } catch (Exception ignored) {

        }


        return Optional.empty();
    }


    @Override
    public long getCurrentProcessStartTime() {
        return getProcessStartTime(getCurrentProcessId());
    }

    @Override
    public long getProcessStartTime(long pid) {
        String path = "/proc/" + pid + "/stat";
        Pointer fp = null;
        try {
            fp = LIBC.fopen(path, "r");
            if (fp == null) {
                return 0;
            }
            byte[] buf = new byte[2048];
            int read = LIBC.fread(buf, 1, buf.length - 1, fp);
            LIBC.fclose(fp);
            if (read <= 0) {
                return 0;
            }
            buf[read] = 0;
            String stat = Native.toString(buf);

            int lParen = stat.indexOf('(');
            int rParen = stat.lastIndexOf(')');
            if (lParen < 0 || rParen < lParen) {
                return 0;
            }
            // Everything after the last ')' is fields #3, #4, #5, … in order
            String after = stat.substring(rParen + 1).trim();
            StringTokenizer st = new StringTokenizer(after);

            if (!st.hasMoreTokens()) return 0;
            st.nextToken(); // field #3: state
            if (!st.hasMoreTokens()) return 0;
            st.nextToken(); // field #4: ppid

            // Skip fields #5…#21.  That's 17 tokens, not 18.
            for (int i = 0; i < 17; i++) {
                if (!st.hasMoreTokens()) return 0;
                st.nextToken();
            }

            // Now the next token is field #22: starttime (in clock ticks since boot)
            if (!st.hasMoreTokens()) return 0;
            long startTicks = Long.parseLong(st.nextToken());

            return BOOT_TIME_MS + (startTicks * 1000L) / CLOCK_TICKS_PER_SECOND;
        } catch (Throwable ignored) {
            if (fp != null) {
                try {
                    LIBC.fclose(fp);
                } catch (Throwable __) {
                }
            }
            return 0;
        }
    }


    //───────────────────────────────────────────────────────────────────────────
    // Linux “/proc/<pid>/stat” via fopen/fread/fclose
    //───────────────────────────────────────────────────────────────────────────

    private interface LinuxCLibrary extends Library {
        Pointer fopen(String path, String mode);

        int fread(byte[] buffer, int size, int nmemb, Pointer stream);

        int fclose(Pointer stream);

        long sysconf(int name);
    }

    private static final LinuxCLibrary LIBC = Native.loadLibrary("c", LinuxCLibrary.class);

    private static final int _SC_CLK_TCK = 2;
    private static final long CLOCK_TICKS_PER_SECOND;
    private static final long BOOT_TIME_MS;

    static {
        long ticks = 100;
        try {
            long t = LIBC.sysconf(_SC_CLK_TCK);
            if (t > 0) ticks = t;
        } catch (Throwable ignored) {
        }
        CLOCK_TICKS_PER_SECOND = ticks;

        long bt = 0;
        Pointer fStat = null;
        try {
            fStat = LIBC.fopen("/proc/stat", "r");
            if (fStat != null) {
                byte[] buf = new byte[4096];
                int len = LIBC.fread(buf, 1, buf.length - 1, fStat);
                LIBC.fclose(fStat);
                if (len > 0) {
                    buf[len] = 0;
                    String stat = Native.toString(buf);
                    for (String line : stat.split("\n")) {
                        if (line.startsWith("btime")) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length >= 2) {
                                bt = Long.parseLong(parts[1]) * 1000L;
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            if (fStat != null) {
                try {
                    LIBC.fclose(fStat);
                } catch (Throwable __) {
                }
            }
        }
        BOOT_TIME_MS = bt;
    }
}
