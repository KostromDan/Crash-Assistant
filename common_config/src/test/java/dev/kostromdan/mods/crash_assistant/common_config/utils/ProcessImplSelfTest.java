package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs every public method on ProcessHelperProcessHandleImpl and (on Windows) ProcessHandleWinImpl,
 * compares each return value against the JDK's ProcessHandle “ground truth,” prints the actual
 * values, and then summarizes pass/fail for each implementation.
 *
 * No production classes are modified—this is purely a test harness.
 *
 * To execute:
 *   javac dev/kostromdan/mods/crash_assistant/common_config/utils/ProcessImplSelfTest.java
 *   java  dev.kostromdan.mods.crash_assistant.common_config.utils.ProcessImplSelfTest
 */
public final class ProcessImplSelfTest {

    public static void main(String[] args) {
        logBanner("STARTING PROCESS-HELPER IMPLEMENTATIONS SELF-TEST");

        // 1) Instantiate each implementation
        ProcessHelperImpl phImpl;
        try {
            phImpl = new ProcessHelperProcessHandleImpl();
            System.out.println("✓ Loaded ProcessHelperProcessHandleImpl");
        } catch (Throwable t) {
            fatal("Could not construct ProcessHelperProcessHandleImpl: " + t);
            return;
        }

        ProcessHandleWinImpl winImpl = null;
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            try {
                winImpl = new ProcessHandleWinImpl();
                System.out.println("✓ Loaded ProcessHandleWinImpl");
            } catch (Throwable t) {
                System.err.println("[WARN] Could not construct ProcessHandleWinImpl: " + t);
            }
        } else {
            System.err.println("[WARN] Non-Windows OS detected; skipping ProcessHandleWinImpl tests.");
        }

        // 2) Run tests on each implementation, collecting pass/fail counts
        TestResult resultPH = runAllTests("ProcessHelperProcessHandleImpl", phImpl);
        TestResult resultWin = null;
        if (winImpl != null) {
            resultWin = runAllTests("ProcessHandleWinImpl", winImpl);
        }

        // 3) Print overall summary
        logBanner("TEST SUMMARY");
        System.out.printf("%-30s : %s%n",
                "ProcessHelperProcessHandleImpl",
                resultPH.passed() ? "PASSED ✅" : "FAILED ❌");
        if (resultWin != null) {
            System.out.printf("%-30s : %s%n",
                    "ProcessHandleWinImpl",
                    resultWin.passed() ? "PASSED ✅" : "FAILED ❌");
        }

        logBanner("SELF-TEST COMPLETE");
    }

    // Encapsulate pass/fail counters for one implementation
    private static final class TestResult {
        int passed = 0, failed = 0;
        void pass()   { passed++; }
        void fail()   { failed++; }
        boolean passed() { return failed == 0; }
    }

    private static TestResult runAllTests(String name, ProcessHelperImpl impl) {
        logBanner("TESTING " + name);
        TestResult result = new TestResult();

        testCurrentProcess(name, impl, result);
        testChildProcesses(name, impl, result);
        testLifeCycle(name, impl, false, result); // graceful destroy
        testLifeCycle(name, impl, true,  result); // forcible destroy

        System.out.printf("[SUMMARY] %-30s : %d passed, %d failed%n",
                name, result.passed, result.failed);
        return result;
    }

    // ──────────────────────── TEST 1 ────────────────────────
    private static void testCurrentProcess(String label,
                                           ProcessHelperImpl impl,
                                           TestResult result) {
        logSection("1) Current-process methods (" + label + ")");

        long truthPid = ProcessHandle.current().pid();
        long pid      = impl.getCurrentProcessId();
        System.out.printf("    getCurrentProcessId() returned: %d    (expected: %d)%n",
                pid, truthPid);
        if (pid == truthPid) result.pass(); else result.fail();

        Optional<String> truthCmd = ProcessHandle.current().info().command();
        Optional<String> cmd = impl.getCurrentProcessCommand();
        System.out.printf("    getCurrentProcessCommand() returned: %s    (expected: %s)%n",
                cmd.orElse("<empty>"),
                truthCmd.orElse("<empty>"));
        if (Objects.equals(cmd, truthCmd)) result.pass(); else result.fail();

        long truthStart = ProcessHandle.current().info()
                .startInstant()
                .map(Instant::toEpochMilli)
                .orElse(-1L);
        long start = impl.getCurrentProcessStartTime();
        System.out.printf("    getCurrentProcessStartTime() returned: %d    (expected: %d)%n",
                start, truthStart);
        if (start == truthStart) result.pass(); else result.fail();

        boolean alive = impl.isProcessAlive(truthPid);
        System.out.printf("    isProcessAlive(currentPid) returned: %b    (expected: true)%n",
                alive);
        if (alive) result.pass(); else result.fail();
    }

    // ──────────────────────── TEST 2 ────────────────────────
    private static void testChildProcesses(String label,
                                           ProcessHelperImpl impl,
                                           TestResult result) {
        logSection("2) Child-process list (" + label + ")");

        String truth = normalizeLines(childInfoTruth());
        String out   = normalizeLines(impl.getChildProcessesInfo());
        System.out.println("    getChildProcessesInfo() returned:\n" + indent(out));
        System.out.println("    Expected (ground truth):\n" + indent(truth));
        if (truth.equals(out)) result.pass(); else result.fail();
    }
    private static String childInfoTruth() {
        return ProcessHandle.current().children()
                .map(ch -> ch.info().startInstant()
                        .map(Instant::toEpochMilli)
                        .map(ms -> ch.pid() + ": " + ms)
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    // ──────────────────────── TEST 3 ────────────────────────
    private static void testLifeCycle(String label,
                                      ProcessHelperImpl impl,
                                      boolean forcible,
                                      TestResult result) {
        logSection("3) Life-cycle (" + label +
                ", " + (forcible ? "forcible" : "graceful") + ")");

        Process child = null;
        try {
            child = spawnSleepyProcess();
            long pid = child.pid();
            TimeUnit.SECONDS.sleep(1); // allow the OS a moment to register the new process

            // 3.1) isProcessAlive()
            boolean alive = impl.isProcessAlive(pid);
            System.out.printf("    isProcessAlive(%d) returned: %b    (expected: true)%n",
                    pid, alive);
            if (alive) result.pass(); else result.fail();

            // 3.2) getProcessStartTime()
            long truthStart = ProcessHandle.of(pid)
                    .flatMap(ph -> ph.info().startInstant())
                    .map(Instant::toEpochMilli)
                    .orElse(-1L);
            long gotStart = impl.getProcessStartTime(pid);
            System.out.printf("    getProcessStartTime(%d) returned: %d    (≈ %d)%n",
                    pid, gotStart, truthStart);
            if (approx(truthStart, gotStart, 10_000)) result.pass(); else result.fail();

            // 3.3) destroy / destroyForcibly
            boolean killed = forcible
                    ? impl.destroyProcessForcibly(pid)
                    : impl.destroyProcess(pid);
            System.out.printf("    %s(%d) returned: %b    (expected: true)%n",
                    (forcible ? "destroyProcessForcibly" : "destroyProcess"),
                    pid, killed);
            if (killed) result.pass(); else result.fail();

            child.waitFor(5, TimeUnit.SECONDS);
            Thread.sleep(100);

            // 3.4) isProcessAlive() after kill
            boolean aliveAfter = impl.isProcessAlive(pid);
            System.out.printf("    isProcessAlive(%d) after kill returned: %b    (expected: false)%n",
                    pid, aliveAfter);
            if (!aliveAfter) result.pass(); else result.fail();

        } catch (Exception e) {
            System.err.println("    [ERROR] Exception during life-cycle test: " + e);
            e.printStackTrace(System.err);
            // Mark all four sub-tests as failed if an exception occurred
            result.fail(); result.fail(); result.fail(); result.fail();
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }

    /** Launches a long‐lived, silent child process. */
    private static Process spawnSleepyProcess() throws Exception {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            // On Windows: use "ping -n 60 127.0.0.1" (≈ 60 seconds), then DISCARD output
            return new ProcessBuilder("ping", "-n", "60", "127.0.0.1")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } else {
            // On Unix/Linux: "sleep 60" is silent
            return new ProcessBuilder("sleep", "60")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
    }

    // ───────────────────── Utility routines ─────────────────────

    private static boolean approx(long a, long b, long delta) {
        return a == -1L || b == -1L || Math.abs(a - b) <= delta;
    }
    private static String normalizeLines(String s) {
        return Stream.of(Optional.ofNullable(s).orElse("").split("\\R"))
                .filter(line -> !line.isBlank())
                .sorted()
                .collect(Collectors.joining("\n"));
    }
    private static String indent(String s) {
        return Arrays.stream(s.split("\\R"))
                .map(line -> "        " + line)
                .collect(Collectors.joining("\n"));
    }
    private static void logBanner(String msg) {
        System.out.println("\n=== " + msg + " ===");
    }
    private static void logSection(String msg) {
        System.out.println("\n--- " + msg + " ---");
    }
    private static void fatal(String msg) {
        System.err.println("[FATAL] " + msg);
        System.exit(1);
    }
}
