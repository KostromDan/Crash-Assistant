package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;

import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LauncherLogger (stderr tee with per-line prefix in the FILE only).
 * <p>
 * Purpose:
 * - Keep original System.err behavior intact on the console.
 * - Write stderr to logs/stderr_stream.log, prefixing each line in the file with "[HH:mm:ss] [STDERR]: ".
 * - System.out is not touched.
 * <p>
 * Behavior:
 * - The log file is recreated on each run (truncate=true).
 * - A short header is written once to the file; it is NOT sent to System.err.
 * - File I/O errors are swallowed to avoid affecting console output.
 * - A JVM shutdown hook restores System.err and closes the file side of the tee.
 * <p>
 */
public final class LauncherLogger {

    private LauncherLogger() {
    }

    // Idempotency guard to avoid double installation in case redirectToFile() is called more than once.
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private static volatile PrintStream ORIGINAL_ERR;
    private static volatile PrintStream INSTALLED_ERR;
    private static volatile Thread SHUTDOWN_HOOK;

    private static final String LOGS_DIR_NAME = "logs";
    private static final String LOG_FILE_NAME = "stderr_stream.log";

    /**
     * Installs a tee on System.err: all bytes still go to the original console,
     * and are also written to logs/stderr_stream.log (with per-line prefix in the file). Idempotent.
     * <p>
     * Header lines are written directly to the file before the tee is attached,
     * so they do NOT appear on the console.
     */
    public static void redirectToFile() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return; // already installed
        }

        boolean systemErrSwapped = false;
        BufferedOutputStream bufferedFile = null;

        try {
            ORIGINAL_ERR = System.err;

            // Prepare logs directory (and guard against "logs" being a regular file).
            File logsDir = new File(LOGS_DIR_NAME);
            if (logsDir.exists() && !logsDir.isDirectory()) {
                ORIGINAL_ERR.println("[LauncherLogger] Path exists and is not a directory: " + logsDir.getAbsolutePath());
                INSTALLED.set(false);
                return;
            }
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                // Never compromise console reliability.
                ORIGINAL_ERR.println("[LauncherLogger] Failed to create logs directory: " + logsDir.getAbsolutePath());
                INSTALLED.set(false);
                return;
            }

            // Open file (truncate = true).
            File logFile = new File(logsDir, LOG_FILE_NAME);
            FileOutputStream fos = new FileOutputStream(logFile, false);
            bufferedFile = new BufferedOutputStream(fos, 64 * 1024);

            // Determine the console charset so the tee preserves console bytes exactly.
            Charset consoleCs = detectConsoleCharset(ORIGINAL_ERR);

            // Write the header ONLY to the file (not to System.err).
            writeFileHeader(bufferedFile, consoleCs);

            // Left side: original console (System.err), wrapped to suppress close().
            OutputStream consoleSide = new NonClosingOutputStream(ORIGINAL_ERR);
            // Right side: the same buffered file stream we just wrote the header to.
            OutputStream fileSide = bufferedFile;

            // Tee: propagate errors from the console side; swallow errors from the file side.
            // Also: flush on '\n' and inject per-line prefix into the FILE side only.
            OutputStream tee = new TeeOutputStream(consoleSide, fileSide, consoleCs);

            // PrintStream with the same charset as the original System.err.
            // autoFlush=true -> flush on println/printf; TeeOutputStream also flushes on '\n' bytes.
            INSTALLED_ERR = new PrintStream(tee, true, consoleCs.name());

            // Swap System.err to the tee.
            System.setErr(INSTALLED_ERR);
            systemErrSwapped = true;

            // Close streams on JVM exit; restore System.err so later hooks still print to console.
            SHUTDOWN_HOOK = new Thread(() -> {
                try {
                    PrintStream ps = INSTALLED_ERR;
                    if (ps != null) {
                        try {
                            System.setErr(ORIGINAL_ERR);
                        } catch (Throwable ignore) {
                        }
                        try {
                            ps.close();
                        } catch (Throwable ignore) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }, "LauncherLogger-Stderr-ShutdownHook");

            try {
                Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
            } catch (Throwable ignored) {
                // Not critical; the tee will still function for the process lifetime.
            }

        } catch (Exception e) {
            // Best-effort rollback; never compromise console reliability.
            try {
                if (systemErrSwapped) {
                    System.setErr(ORIGINAL_ERR);
                }
            } catch (Throwable ignored) {
            }

            try {
                if (INSTALLED_ERR != null) INSTALLED_ERR.close();
            } catch (Throwable ignored) {
            }
            INSTALLED_ERR = null;

            try {
                if (bufferedFile != null) bufferedFile.close();
            } catch (IOException ignored) {
            }

            if (SHUTDOWN_HOOK != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(SHUTDOWN_HOOK);
                } catch (Throwable ignored) {
                }
                SHUTDOWN_HOOK = null;
            }

            try {
                if (ORIGINAL_ERR != null) e.printStackTrace(ORIGINAL_ERR);
                else e.printStackTrace();
            } finally {
                INSTALLED.set(false);
            }
        }
    }

    /**
     * Writes a one-time header to the log file (ASCII-only text), using the provided charset
     * so the file remains consistent with the console encoding used by the tee.
     * The header is flushed before the tee is attached.
     */
    private static void writeFileHeader(BufferedOutputStream out, Charset cs) throws IOException {
        // Example format: "12.11.2025 22:21:15:749 +03:00" (ASCII-safe)
        final String ls = System.lineSeparator();
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss:SSS XXX", java.util.Locale.ROOT);
        df.setTimeZone(TimeZone.getDefault());
        String dateTime = df.format(new Date());

        StringBuilder sb = new StringBuilder(256);
        sb.append("-----------------------------------------------------------------------------------").append(ls);
        sb.append("DateTime: ").append(dateTime).append("; PID: ").append(ProcessHelper.getCurrentProcessId()).append(ls);
        sb.append("This file contains the stderr stream of the Minecraft process logged to a file.").append(ls);
        sb.append("It does not include stdout or log (log4j) messages to avoid performance impact.").append(ls);
        sb.append("-----------------------------------------------------------------------------------").append(ls);

        out.write(sb.toString().getBytes(cs));
        out.flush();
    }

    /**
     * Attempts to detect the charset of the original console PrintStream to replicate it.
     * Order:
     * 1) JVM properties: "sun.stderr.encoding", "sun.stdout.encoding", "native.encoding", then "file.encoding".
     * 2) JDK >= 10: private PrintStream#charset() via reflection (last resort; may warn on some JDKs).
     * 3) Fallback: Charset.defaultCharset().
     */
    private static Charset detectConsoleCharset(PrintStream ps) {
        // JVM properties first (no reflective warnings).
        final String[] props = {"sun.stderr.encoding", "sun.stdout.encoding", "native.encoding", "file.encoding"};
        for (String p : props) {
            try {
                String v = System.getProperty(p);
                if (v != null && !v.isEmpty()) {
                    try {
                        return Charset.forName(v);
                    } catch (Throwable ignore) {
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        // Reflection last.
        try {
            java.lang.reflect.Method m = PrintStream.class.getDeclaredMethod("charset");
            m.setAccessible(true);
            Object cs = m.invoke(ps);
            if (cs instanceof Charset) return (Charset) cs;
        } catch (Throwable ignore) {
        }

        return Charset.defaultCharset();
    }

    /**
     * OutputStream that writes to the left (console) and right (file).
     * If the left side throws, rethrow it; right-side exceptions are swallowed.
     * Additionally:
     * - Detects '\n' and flushes both sides immediately (line-buffered effect).
     * - Injects a per-line prefix into the FILE side only:
     * "[HH:mm:ss.SSS] [STDERR]: "
     * The very first stderr write also receives the prefix in the file.
     */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream left;    // original console (System.err)
        private final OutputStream right;   // file stream
        private final Charset cs;           // file side encoding for prefix
        private boolean atLineStart = true; // start of line for FILE side prefixing

        // Timestamp utilities (no allocation per line beyond small strings)
        private final SimpleDateFormat timeFmt =
                new SimpleDateFormat("HH:mm:ss:SSS", java.util.Locale.ROOT);
        private final Date tsReuse = new Date();

        TeeOutputStream(OutputStream left, OutputStream right, Charset cs) {
            this.left = left;
            this.right = right;
            this.cs = cs;
        }

        @Override
        public void write(int b) throws IOException {
            // Prefix in file at the beginning of a line
            if (atLineStart) {
                writePrefixToFile();
                atLineStart = false;
            }

            IOException leftEx = null;
            try {
                left.write(b);
            } catch (IOException e) {
                leftEx = e;
            }
            try {
                right.write(b);
            } catch (IOException ignored) {
            }

            if (b == '\n') {
                // Flush both sides immediately at end-of-line
                try {
                    right.flush();
                } catch (IOException ignored) {
                }
                try {
                    left.flush();
                } catch (IOException e) {
                    if (leftEx == null) leftEx = e;
                }
                atLineStart = true; // next byte begins a new line (prefix will be injected)
            }

            if (leftEx != null) throw leftEx;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int i = off;
            final int end = off + len;
            IOException leftEx = null;

            while (i < end) {
                // Inject prefix in the file at the beginning of the line
                if (atLineStart) {
                    writePrefixToFile();
                    atLineStart = false;
                }

                // Find next newline
                int j = i;
                while (j < end && b[j] != (byte) '\n') j++;

                // Segment length (without newline if not found; with newline if found)
                int chunkLen = (j < end && b[j] == (byte) '\n') ? (j - i + 1) : (j - i);

                // Write the chunk to both sides
                try {
                    left.write(b, i, chunkLen);
                } catch (IOException e) {
                    if (leftEx == null) leftEx = e;
                }
                try {
                    right.write(b, i, chunkLen);
                } catch (IOException ignored) {
                }

                // If the chunk ended with a newline, flush and mark line start for next segment
                if (chunkLen > 0 && b[i + chunkLen - 1] == (byte) '\n') {
                    try {
                        right.flush();
                    } catch (IOException ignored) {
                    }
                    try {
                        left.flush();
                    } catch (IOException e) {
                        if (leftEx == null) leftEx = e;
                    }
                    atLineStart = true;
                }

                i += chunkLen;
            }

            if (leftEx != null) throw leftEx;
        }

        @Override
        public void flush() throws IOException {
            IOException leftEx = null;
            try {
                left.flush();
            } catch (IOException e) {
                leftEx = e;
            }
            try {
                right.flush();
            } catch (IOException ignored) {
            }
            if (leftEx != null) throw leftEx;
        }

        @Override
        public void close() throws IOException {
            try {
                left.close();  // NonClosingOutputStream: flush-only for the real System.err
            } finally {
                try {
                    right.close();
                } catch (IOException ignored) {
                }
            }
        }

        // Compose "[HH:mm:ss] [STDERR]: " and write to FILE side using the configured charset.
        private void writePrefixToFile() {
            try {
                tsReuse.setTime(System.currentTimeMillis());
                String t = timeFmt.format(tsReuse);
                // Build prefix without allocations beyond a small StringBuilder
                StringBuilder sb = new StringBuilder(20 + 12); // "[HH:mm:ss] [STDERR]: " ~ 22 chars
                sb.append('[').append(t).append(']').append(' ').append("[STDERR]: ").append("");
                byte[] bytes = sb.toString().getBytes(cs);
                right.write(bytes);
            } catch (Throwable ignored) {
                // Never let prefixing disturb console output reliability
            }
        }
    }

    /**
     * OutputStream wrapper that suppresses close() (flushes only).
     * Prevents closing the real System.err when the tee is closed.
     */
    private static final class NonClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        NonClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        } // tiny micro-optimisation

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            // Do not close the real System.err; flush only.
            delegate.flush();
        }
    }
}
