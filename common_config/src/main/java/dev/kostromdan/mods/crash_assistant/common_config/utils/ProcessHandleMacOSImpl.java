package dev.kostromdan.mods.crash_assistant.common_config.utils;

import dev.kostromdan.mods.crash_assistant.common_config.loading_utils.JarInJarHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * macOS implementation that uses the platform’s own <code>ps(1)</code>
 * binary instead of JNA.  No native libraries are loaded.
 *
 * <p>Behavioural contract kept identical:</p>
 * <ul>
 *   <li>{@code getCurrentProcessCommand()} returns the first token of the
 *       launcher command exactly as the kernel recorded it (e.g. {@code java}
 *       or {@code /Library/Java/…/bin/java}).</li>
 *   <li>{@code getProcessStartTime(pid)} returns a millisecond value that is
 *       stable for the entire lifetime of that process; subsequent calls for
 *       the same PID yield the <em>exact</em> same number (millisecond
 *       precision).</li>
 * </ul>
 */
public class ProcessHandleMacOSImpl extends ProcessHandleUnixAbstractImpl {

    /* ---------- Public API ------------------------------------------------ */

    @Override
    public Optional<String> getCurrentProcessCommand() {
        return getExecutablePath(getCurrentProcessId());
    }

    @Override
    public long getCurrentProcessStartTime() {
        return getProcessStartTime(getCurrentProcessId());
    }

    @Override
    public long getProcessStartTime(long pid) {
        try {
            if (!isProcessAlive(pid)) {
                return -1;
            }

            String lstart = execAndReadFirst(
                    "ps", "-p", String.valueOf(pid),
                    "-o", "lstart="
            );

            if (lstart == null || lstart.trim().isEmpty()) {
                return 0;
            }

            LocalDateTime ldt = LocalDateTime.parse(lstart.trim(), PS_LSTART);
            return ldt.atZone(LOCAL_ZONE).toInstant().toEpochMilli();
        } catch (Throwable ignored) {
            return 0;
        }
    }


    /* ---------- Internals ------------------------------------------------- */

    private Optional<String> getExecutablePath(long pid) {
        try {
            Process p = new ProcessBuilder("lsof",
                    "-p", String.valueOf(pid),
                    "-a", "-d", "txt",
                    "-Fn")               // parse-friendly output
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("n") && line.length() > 1) {
                        return Optional.of(line.substring(1));
                    }
                }
            } finally {
                p.destroy();
            }
        } catch (Throwable e) {
            JarInJarHelper.LOGGER.error("Unable to determine executable path for PID {}", pid, e);
        }


        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isEmpty()) {
            Path cmd = Paths.get(javaHome, "bin", "java");
            if (Files.isExecutable(cmd)) return Optional.of(cmd.toString());
        }

        return Optional.empty();
    }

    /**
     * Kernel start-time format that macOS’ <code>ps(1)</code> prints for “lstart”.
     */
    private static final DateTimeFormatter PS_LSTART =
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);

    /**
     * Cached once to avoid ZoneRules lookup on every call.
     */
    private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault();
}
