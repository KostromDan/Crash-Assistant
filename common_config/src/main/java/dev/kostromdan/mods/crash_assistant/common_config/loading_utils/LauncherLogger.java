package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LauncherLogger
 * <p>
 * Redirects System.out and System.err to a single file (logs/stdout_stderr_streams.log)
 * while keeping console output unchanged.
 * <p>
 * Details:
 * - The log file is truncated on each startup.
 * - The file receives entries prefixed only on the FIRST line of a multi-line write:
 * "[HH:mm:ss] [STDOUT]: " or "[HH:mm:ss] [STDERR]: ".
 * Subsequent lines in the same write are NOT prefixed.
 * - Console output remains unmodified (no prefixes, same bytes/charset as before).
 * - Writes to the file are synchronized per line to avoid mid-line interleaving
 * between stdout and stderr.
 * - A shutdown hook flushes any trailing partial line and closes the file.
 */
public final class LauncherLogger {

    private LauncherLogger() {
    }

    // Shared lock for atomic line writes across both prefixed streams.
    private static final Object FILE_LINE_LOCK = new Object();

    // Install guard and handles needed for clean shutdown.
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static BufferedOutputStream FILE_OUT;
    private static LinePrefixingOutputStream FILE_SIDE_OUT;
    private static LinePrefixingOutputStream FILE_SIDE_ERR;
    private static PrintStream ORIGINAL_OUT;
    private static PrintStream ORIGINAL_ERR;
    private static PrintStream INSTALLED_OUT;
    private static PrintStream INSTALLED_ERR;

    // Timestamp for prefixes: [HH:mm:ss]
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void redirectToFile() {
        if (!INSTALLED.compareAndSet(false, true)) {
            // Already installed; no-op
            return;
        }

        BufferedOutputStream bufferedFile = null;

        try {
            // Capture original console streams up-front.
            ORIGINAL_OUT = System.out;
            ORIGINAL_ERR = System.err;

            // Detect the console charset so forwarded bytes match exactly what the console expects.
            Charset consoleCs = detectConsoleCharset(ORIGINAL_OUT);

            File logsDir = new File("logs");
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                // Use originalErr to avoid any re-entrancy if installation fails mid-way.
                ORIGINAL_ERR.println("Failed to create logs directory: " + logsDir.getAbsolutePath());
                INSTALLED.set(false);
                return;
            }

            File logFile = new File(logsDir, "stdout_stderr_streams.log");

            // Truncate the file on each run and keep it open.
            FileOutputStream fos = new FileOutputStream(logFile, false);
            bufferedFile = new BufferedOutputStream(fos, 64 * 1024);

            // Bootstrap lines go straight to the file (no prefixes, synchronized for consistency).
            synchronized (FILE_LINE_LOCK) {
                writeBootstrapLine(bufferedFile, "[CrashAssistantLauncherLogger]: Streams logging initialized: " + logFile.getAbsolutePath(), consoleCs);
                writeBootstrapLine(bufferedFile, "[CrashAssistantLauncherLogger]: Warning! Unlike usual launcher logs, this file contains ONLY stdout/stderr streams.", consoleCs);
                writeBootstrapLine(bufferedFile, "[CrashAssistantLauncherLogger]: This is intentional to avoid impacting performance.", consoleCs);
                bufferedFile.flush();
            }

            // File-side prefixed streams. Prefix appears only on the FIRST line per write call.
            FILE_SIDE_OUT = new LinePrefixingOutputStream(bufferedFile, "STDOUT", FILE_LINE_LOCK, consoleCs);
            FILE_SIDE_ERR = new LinePrefixingOutputStream(bufferedFile, "STDERR", FILE_LINE_LOCK, consoleCs);

            // Console-side wrappers that ignore close(); they forward bytes exactly as-is.
            OutputStream consoleOut = new NonClosingOutputStream(ORIGINAL_OUT);
            OutputStream consoleErr = new NonClosingOutputStream(ORIGINAL_ERR);

            // Tee to console (left) and file (right). Console always wins if file fails.
            OutputStream teeOut = new TeeOutputStream(consoleOut, FILE_SIDE_OUT);
            OutputStream teeErr = new TeeOutputStream(consoleErr, FILE_SIDE_ERR);

            // Use the console's charset so the bytes forwarded to the original console are identical.
            // (Use the String-charset constructor for JDK8 compatibility.)
            INSTALLED_OUT = new PrintStream(teeOut, true, consoleCs.name());
            INSTALLED_ERR = new PrintStream(teeErr, true, consoleCs.name());

            System.setOut(INSTALLED_OUT);
            System.setErr(INSTALLED_ERR);

            INSTALLED_OUT.flush();
            INSTALLED_ERR.flush();

            FILE_OUT = bufferedFile;

            // Ensure trailing partial lines get written and the file is closed on JVM shutdown.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (FILE_SIDE_OUT != null) FILE_SIDE_OUT.close();
                } catch (IOException ignored) {
                }
                try {
                    if (FILE_SIDE_ERR != null) FILE_SIDE_ERR.close();
                } catch (IOException ignored) {
                }
                try {
                    if (FILE_OUT != null) FILE_OUT.close();
                } catch (IOException ignored) {
                }
            }));

        } catch (Exception e) {
            // If anything goes wrong during install, report via the original stderr to avoid recursion.
            try {
                if (ORIGINAL_ERR != null) e.printStackTrace(ORIGINAL_ERR);
                else e.printStackTrace();
            } catch (Throwable t) {
                e.printStackTrace();
            } finally {
                INSTALLED.set(false);
            }
        }
    }

    /**
     * Writes a single line directly to the file, terminated by '\n', using the given charset.
     */
    private static void writeBootstrapLine(BufferedOutputStream file, String line, Charset cs) throws IOException {
        file.write(line.getBytes(cs));
        file.write('\n');
    }

    /**
     * Attempt to discover the charset actually used by the console PrintStream so
     * forwarded bytes are identical to the original behavior.
     */
    private static Charset detectConsoleCharset(PrintStream ps) {
        // JDK ≥ 10: PrintStream has a private 'charset()' method.
        try {
            java.lang.reflect.Method m = PrintStream.class.getDeclaredMethod("charset");
            m.setAccessible(true);
            Object cs = m.invoke(ps);
            if (cs instanceof Charset) return (Charset) cs;
        } catch (Throwable ignore) {
        }
        // Some JVMs expose system properties:
        try {
            String prop = System.getProperty("sun.stdout.encoding");
            if (prop != null && !prop.isEmpty()) return Charset.forName(prop);
        } catch (Throwable ignore) {
        }
        // Best effort fallback.
        return Charset.defaultCharset();
    }

    /**
     * Splits writes to two OutputStreams. Console (left) is prioritized:
     * errors on the file side are swallowed so the console remains unaffected.
     */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream left;   // console
        private final OutputStream right;  // file

        TeeOutputStream(OutputStream left, OutputStream right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public void write(int b) throws IOException {
            IOException consoleEx = null;
            try {
                left.write(b);
            } catch (IOException e) {
                consoleEx = e;
            }
            try {
                right.write(b);
            } catch (IOException ignored) {
            }
            if (consoleEx != null) throw consoleEx;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            IOException consoleEx = null;
            try {
                left.write(b, off, len);
            } catch (IOException e) {
                consoleEx = e;
            }
            try {
                right.write(b, off, len);
            } catch (IOException ignored) {
            }
            if (consoleEx != null) throw consoleEx;
        }

        @Override
        public void flush() throws IOException {
            IOException consoleEx = null;
            try {
                left.flush();
            } catch (IOException e) {
                consoleEx = e;
            }
            try {
                right.flush();
            } catch (IOException ignored) {
            }
            if (consoleEx != null) throw consoleEx;
        }

        @Override
        public void close() throws IOException {
            try {
                left.close();  // NonClosingOutputStream -> flush only
            } finally {
                try {
                    right.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * OutputStream wrapper that suppresses close() on the underlying stream.
     * Used to protect the original System.out/System.err.
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
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            // Do not close System streams; just flush.
            delegate.flush();
        }
    }

    /**
     * Adds a timestamped, labeled prefix ONLY to the FIRST line produced by a single write(...) call.
     * Subsequent lines from the same write are emitted without a prefix.
     * CR, LF, and CRLF are handled; on close, a trailing partial line is flushed (with a prefix).
     */
    private static final class LinePrefixingOutputStream extends OutputStream {
        private final OutputStream delegate;   // shared BufferedOutputStream
        private final String label;            // "STDOUT" or "STDERR"
        private final Object lineLock;         // shared lock across both streams
        private final Charset charset;         // same as console charset

        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(4096);
        private boolean sawCR = false;
        private boolean closed = false;

        LinePrefixingOutputStream(OutputStream delegate, String label, Object lineLock, Charset charset) {
            this.delegate = delegate;
            this.label = label;
            this.lineLock = lineLock;
            this.charset = charset;
        }

        @Override
        public void write(int b) throws IOException {
            // route through array path; per-byte writes are rare from PrintStream
            byte[] one = {(byte) b};
            write(one, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ensureOpen();

            boolean prefixForNextLineInThisCall = true; // only first emitted line gets a prefix

            int i = off;
            int end = off + len;
            while (i < end) {
                int ch = b[i] & 0xFF;
                i++;

                if (sawCR) {
                    if (ch == '\n') {
                        emitLine(/*fromCRorCRLF*/true, /*prefixThisLine*/prefixForNextLineInThisCall);
                        prefixForNextLineInThisCall = false;
                        sawCR = false;
                        continue;
                    } else {
                        emitLine(/*fromCRorCRLF*/true, /*prefixThisLine*/prefixForNextLineInThisCall); // lone CR
                        prefixForNextLineInThisCall = false;
                        sawCR = false;
                        // fall through to process current ch
                    }
                }

                if (ch == '\r') {
                    sawCR = true;
                } else if (ch == '\n') {
                    emitLine(/*fromCRorCRLF*/false, /*prefixThisLine*/prefixForNextLineInThisCall);
                    prefixForNextLineInThisCall = false;
                } else {
                    lineBuffer.write(ch);
                }
            }
        }

        @Override
        public void flush() throws IOException {
            // Coherent with writes; do not emit partial line here.
            synchronized (lineLock) {
                delegate.flush();
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;

            // Flush a trailing partial line (prefix it).
            if (sawCR) {
                emitLine(true, true);
                sawCR = false;
            } else if (lineBuffer.size() > 0) {
                emitLine(false, true);
            }

            synchronized (lineLock) {
                delegate.flush();
            }
        }

        private void emitLine(boolean fromCRorCRLF, boolean prefixThisLine) throws IOException {
            synchronized (lineLock) {
                if (prefixThisLine) {
                    String ts = LocalTime.now().format(TIME_FMT);
                    String prefix = "[" + ts + "] [" + label + "]: ";
                    delegate.write(prefix.getBytes(charset));
                }
                lineBuffer.writeTo(delegate);
                if (fromCRorCRLF) {
                    delegate.write('\r');
                    delegate.write('\n');
                } else {
                    delegate.write('\n');
                }
            }
            lineBuffer.reset();
        }

        private void ensureOpen() throws IOException {
            if (closed) throw new IOException("Stream closed");
        }
    }
}
