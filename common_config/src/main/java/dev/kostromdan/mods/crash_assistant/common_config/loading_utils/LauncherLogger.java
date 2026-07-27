package dev.kostromdan.mods.crash_assistant.common_config.loading_utils;

import dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessHelper;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides asynchronous redirection of {@code System.err} to a file and the console.
 * <p>
 * This class utilizes a background daemon thread to decouple I/O operations from the application's
 * main thread, preventing blocking or deadlocks caused by synchronous I/O or lock contention
 * on the standard error stream. It employs reflection to inject the asynchronous stream into
 * the existing {@code System.err} pipeline and attempts to bypass synchronization locks by
 * writing directly to {@code FileDescriptor.err}.
 */
public final class LauncherLogger {

    private LauncherLogger() {
    }

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static volatile Thread SHUTDOWN_HOOK;
    private static volatile AsyncLogWriter activeWorker;

    // Queue for buffering log events; unbounded to prioritize application liveness over memory limits.
    private static final BlockingQueue<LogEvent> LOG_QUEUE = new LinkedBlockingQueue<>();

    private static final String LOGS_DIR_NAME = "logs";
    private static final String LOG_FILE_NAME = "stderr_stream.log";

    /**
     * Installs the asynchronous logging redirection.
     * <p>
     * This method initializes the log file, starts the background writer thread, and
     * replaces the current {@code System.err} stream. It attempts to inject the
     * asynchronous stream into the existing stream chain to capture output from
     * loggers holding direct references to the previous stream.
     * <p>
     * If initialization fails, the state is reverted to ensure system stability.
     */
    public static void redirectToFile() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        PrintStream originalErr = System.err;

        try {
            File logsDir = new File(LOGS_DIR_NAME);
            if (logsDir.exists() && !logsDir.isDirectory()) {
                JarInJarHelper.LOGGER.error("Logs path exists but is not a directory: {}", logsDir.getAbsolutePath());
                INSTALLED.set(false);
                return;
            }
            if (!logsDir.exists() && !logsDir.mkdirs()) {
                JarInJarHelper.LOGGER.error("Failed to create logs directory: {}", logsDir.getAbsolutePath());
                INSTALLED.set(false);
                return;
            }

            File logFile = new File(logsDir, LOG_FILE_NAME);
            Charset consoleCs = detectConsoleCharset(originalErr);

            // Attempt to establish a direct channel to the console file descriptor.
            OutputStream directConsoleStream = null;
            try {
                directConsoleStream = new FileOutputStream(FileDescriptor.err);
            } catch (Throwable t) {
                JarInJarHelper.LOGGER.warn("FileDescriptor.err unavailable; console mirroring disabled.");
            }

            AsyncLogWriter worker = new AsyncLogWriter();
            worker.init(logFile, directConsoleStream, consoleCs);
            worker.start();
            activeWorker = worker;

            OutputStream queueStream = new AsyncQueueOutputStream();

            // Inject the queue into the existing FilterOutputStream chain.
            boolean injectionSuccess = injectAsyncStream(originalErr, queueStream);

            if (!injectionSuccess) {
//                JarInJarHelper.LOGGER.warn("Stream injection failed; legacy loggers may retain blocking behavior.");
            }

            // Update System.err reference for new consumers.
            try {
                PrintStream proxyStream = new PrintStream(queueStream, true, consoleCs.name());
                System.setErr(proxyStream);
            } catch (Throwable t) {
                JarInJarHelper.LOGGER.warn("Failed to update System.err proxy.");
            }

            try {
                SHUTDOWN_HOOK = new Thread(() -> {
                    AsyncLogWriter w = activeWorker;
                    if (w != null) {
                        w.signalShutdown();
                    }
                }, "LauncherLogger-ShutdownHook");

                Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
            } catch (Throwable t) {
                JarInJarHelper.LOGGER.warn("Failed to register shutdown hook.");
            }

        } catch (Exception e) {
            // Revert installation state on failure.
            try {
                System.setErr(originalErr);
            } catch (Exception ignored) {
            }

            if (activeWorker != null) {
                activeWorker.signalShutdown();
                activeWorker = null;
            }
            INSTALLED.set(false);
            JarInJarHelper.LOGGER.error("Error while redirecting LauncherLogger to file: ", e);
        }
    }

    /**
     * Traverses the {@code FilterOutputStream} chain of the provided stream and replaces the
     * underlying {@code OutputStream} with the specified replacement.
     *
     * @param stream      the target {@code PrintStream} to modify
     * @param replacement the new {@code OutputStream} to inject
     * @return {@code true} if the injection was successful, {@code false} otherwise
     */
    private static boolean injectAsyncStream(PrintStream stream, OutputStream replacement) {
        if (!(stream instanceof FilterOutputStream)) {
            return false;
        }

        try {
            OutputStream current = stream;
            Field outField = FilterOutputStream.class.getDeclaredField("out");
            outField.setAccessible(true);

            while (true) {
                Object downstream = outField.get(current);

                if (downstream instanceof FilterOutputStream) {
                    current = (OutputStream) downstream;
                } else {
                    outField.set(current, replacement);
                    return true;
                }
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Represents a buffered log operation or a control signal (e.g., flush).
     */
    private static class LogEvent {
        final byte[] data;
        final long timestamp;
        final boolean isFlush;

        LogEvent(byte[] data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.isFlush = false;
        }

        LogEvent(int singleByte) {
            this.data = new byte[]{(byte) singleByte};
            this.timestamp = System.currentTimeMillis();
            this.isFlush = false;
        }

        LogEvent(boolean isFlush) {
            this.data = null;
            this.timestamp = 0;
            this.isFlush = isFlush;
        }
    }

    /**
     * Daemon thread responsible for consuming log events and performing blocking I/O operations.
     */
    private static class AsyncLogWriter extends Thread {
        private BufferedOutputStream fileOut;
        private OutputStream rawConsoleOut;
        private Charset charset;
        private volatile boolean running = true;
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss:SSS", java.util.Locale.ROOT);
        private final Date dateReuse = new Date();
        private boolean atLineStart = true;

        AsyncLogWriter() {
            super("LauncherLogger-Worker");
            setDaemon(true);
        }

        void init(File file, OutputStream rawConsole, Charset cs) throws FileNotFoundException {
            this.charset = cs;
            this.rawConsoleOut = rawConsole != null ? new BufferedOutputStream(rawConsole) : null;
            this.fileOut = new BufferedOutputStream(new FileOutputStream(file, false), 64 * 1024);
            try {
                writeHeader();
            } catch (IOException ignored) {
            }
        }

        void signalShutdown() {
            running = false;
            this.interrupt();
            try {
                this.join(2000);
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void run() {
            while (running || !LOG_QUEUE.isEmpty()) {
                try {
                    LogEvent event = LOG_QUEUE.poll(500, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        if (event.isFlush) {
                            performFlush();
                        } else {
                            processData(event);
                        }
                    }
                } catch (InterruptedException e) {
                    // Thread interrupted during shutdown.
                } catch (Exception e) {
                    // Suppress exceptions to keep the worker alive.
                }
            }
            performFlush();
            closeStreams();
        }

        private void processData(LogEvent event) {
            byte[] b = event.data;
            int len = b.length;

            try {
                dateReuse.setTime(event.timestamp);
                String timeStr = timeFmt.format(dateReuse);
                byte[] prefixBytes = null;

                for (int i = 0; i < len; i++) {
                    if (atLineStart) {
                        if (prefixBytes == null) {
                            prefixBytes = ("[" + timeStr + "] [STDERR]: ").getBytes(charset);
                        }
                        fileOut.write(prefixBytes);
                        atLineStart = false;
                    }

                    fileOut.write(b[i]);

                    if (b[i] == '\n') {
                        atLineStart = true;
                    }
                }
            } catch (IOException ignored) {
            }

            if (rawConsoleOut != null) {
                try {
                    rawConsoleOut.write(b);
                } catch (IOException ignored) {
                }
            }
        }

        private void performFlush() {
            try {
                if (fileOut != null) fileOut.flush();
            } catch (IOException ignored) {
            }
            try {
                if (rawConsoleOut != null) rawConsoleOut.flush();
            } catch (IOException ignored) {
            }
        }

        private void writeHeader() throws IOException {
            final String ls = System.lineSeparator();
            SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss:SSS XXX", java.util.Locale.ROOT);
            df.setTimeZone(TimeZone.getDefault());
            StringBuilder sb = new StringBuilder();
            sb.append("-----------------------------------------------------------------------------------").append(ls);
            sb.append("DateTime: ").append(df.format(new Date())).append("; PID: ").append(ProcessHelper.getCurrentProcessId()).append(ls);
            sb.append("This file contains the stderr stream of the Minecraft process logged to a file.").append(ls);
            sb.append("It does not include stdout or log (log4j) messages to avoid performance impact.").append(ls);
            sb.append("-----------------------------------------------------------------------------------").append(ls);
            fileOut.write(sb.toString().getBytes(charset));
            fileOut.flush();
        }

        private void closeStreams() {
            try {
                if (fileOut != null) {
                    fileOut.flush();
                    fileOut.close();
                }
            } catch (IOException ignored) {
            }

            try {
                if (rawConsoleOut != null) {
                    rawConsoleOut.flush();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * An {@code OutputStream} implementation that acts as a producer for the asynchronous log queue.
     */
    private static final class AsyncQueueOutputStream extends OutputStream {
        @Override
        public void write(int b) {
            LOG_QUEUE.offer(new LogEvent(b));
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();

            byte[] copy = new byte[len];
            System.arraycopy(b, off, copy, 0, len);
            LOG_QUEUE.offer(new LogEvent(copy));
        }

        @Override
        public void flush() {
            LOG_QUEUE.offer(new LogEvent(true));
        }

        @Override
        public void close() {
            flush();
        }
    }

    private static Charset detectConsoleCharset(PrintStream ps) {
        final String[] props = {"sun.stderr.encoding", "sun.stdout.encoding", "native.encoding", "file.encoding"};
        for (String p : props) {
            try {
                String v = System.getProperty(p);
                if (v != null && !v.isEmpty()) return Charset.forName(v);
            } catch (Throwable ignore) {
            }
        }
        try {
            java.lang.reflect.Method m = PrintStream.class.getDeclaredMethod("charset");
            m.setAccessible(true);
            return (Charset) m.invoke(ps);
        } catch (Throwable ignore) {
        }
        return Charset.defaultCharset();
    }
}