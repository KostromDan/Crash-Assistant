package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reflection-based {@link ProcessHelperImpl} that calls
 * {@code java.lang.ProcessHandle} only when it is present (JDK 9 +).
 * <p>
 * All expensive reflective look-ups are performed <em>once</em> in the
 * constructor and cached as {@link MethodHandle}s, so the per-call overhead is
 * negligible.  The class still compiles on Java 8 because it never references
 * {@code ProcessHandle} at compile time.
 */
@SuppressWarnings({"unchecked", "OptionalUsedAsFieldOrParameterType"})
public final class ProcessHelperProcessHandleImpl implements ProcessHelperImpl {

    // === cached handles ====================================================

    private final MethodHandle mh_current;
    private final MethodHandle mh_of;
    private final MethodHandle mh_pid;
    private final MethodHandle mh_info;
    private final MethodHandle mh_command;
    private final MethodHandle mh_startInstant;
    private final MethodHandle mh_isAlive;
    private final MethodHandle mh_children;
    private final MethodHandle mh_destroy;
    private final MethodHandle mh_destroyForcibly;

    // === ctor ==============================================================

    public ProcessHelperProcessHandleImpl() {
        try {
            Class<?> phClass = Class.forName("java.lang.ProcessHandle");
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            mh_current = lookup.findStatic(phClass, "current",
                    MethodType.methodType(phClass));
            mh_of = lookup.findStatic(phClass, "of",
                    MethodType.methodType(Optional.class, long.class));
            mh_pid = lookup.findVirtual(phClass, "pid",
                    MethodType.methodType(long.class));
            mh_info = lookup.findVirtual(phClass, "info",
                    MethodType.methodType(infoClass));
            mh_command = lookup.findVirtual(infoClass, "command",
                    MethodType.methodType(Optional.class));
            mh_startInstant = lookup.findVirtual(infoClass, "startInstant",
                    MethodType.methodType(Optional.class));
            mh_isAlive = lookup.findVirtual(phClass, "isAlive",
                    MethodType.methodType(boolean.class));
            mh_children = lookup.findVirtual(phClass, "children",
                    MethodType.methodType(Stream.class));
            mh_destroy = lookup.findVirtual(phClass, "destroy",
                    MethodType.methodType(boolean.class));
            mh_destroyForcibly = lookup.findVirtual(phClass, "destroyForcibly",
                    MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "ProcessHandle API not found – are you on Java 9+ ?", e);
        }
    }

    // === public API ========================================================

    @Override
    public long getCurrentProcessId() {
        try {
            Object ph = mh_current.invoke();
            return (long) mh_pid.invoke(ph);
        } catch (Throwable t) {
            throw fail("current PID", t);
        }
    }

    @Override
    public Optional<String> getCurrentProcessCommand() {
        try {
            Object ph = mh_current.invoke();
            Object info = mh_info.invoke(ph);
            return (Optional<String>) mh_command.invoke(info);
        } catch (Throwable t) {
            throw fail("current command", t);
        }
    }

    @Override
    public long getCurrentProcessStartTime() {
        try {
            Object ph = mh_current.invoke();
            Object info = mh_info.invoke(ph);
            Optional<Instant> ins = (Optional<Instant>) mh_startInstant.invoke(info);
            return ins.map(Instant::toEpochMilli).orElse(-1L);
        } catch (Throwable t) {
            throw fail("current start time", t);
        }
    }

    @Override
    public long getProcessStartTime(long pid) {
        try {
            Object ph = handleOf(pid);
            if (ph == null) return -1;
            Object info = mh_info.invoke(ph);
            Optional<Instant> ins = (Optional<Instant>) mh_startInstant.invoke(info);
            return ins.map(Instant::toEpochMilli).orElse(-1L);
        } catch (Throwable t) {
            throw fail("start time for pid " + pid, t);
        }
    }

    @Override
    public boolean isProcessAlive(long pid) {
        try {
            Object ph = handleOf(pid);
            return ph != null && (boolean) mh_isAlive.invoke(ph);
        } catch (Throwable t) {
            throw fail("liveness check for pid " + pid, t);
        }
    }

    @Override
    public String getChildProcessesInfo() {
        try {
            Object current = mh_current.invoke();
            Stream<?> children = (Stream<?>) mh_children.invoke(current);

            // build "pid: epochMillis" lines
            String joined = children
                    .map(ph -> {
                        try {
                            long cpid = (long) mh_pid.invoke(ph);
                            Object info = mh_info.invoke(ph);
                            Optional<Instant> ins =
                                    (Optional<Instant>) mh_startInstant.invoke(info);
                            return ins.map(i -> cpid + ": " + i.toEpochMilli()).orElse(null);
                        } catch (Throwable t) {
                            return null; // skip on error
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));

            children.close();
            return joined;
        } catch (Throwable t) {
            throw fail("child-process list", t);
        }
    }

    @Override
    public boolean destroyProcess(long pid) {
        try {
            Object ph = handleOf(pid);
            return ph != null && (boolean) mh_destroy.invoke(ph);
        } catch (Throwable t) {
            throw fail("destroy pid " + pid, t);
        }
    }

    @Override
    public boolean destroyProcessForcibly(long pid) {
        try {
            Object ph = handleOf(pid);
            return ph != null && (boolean) mh_destroyForcibly.invoke(ph);
        } catch (Throwable t) {
            throw fail("forcible destroy pid " + pid, t);
        }
    }

    // === helpers ===========================================================

    /**
     * Returns the {@code ProcessHandle} object for the given PID or
     * {@code null} if no such process exists.
     */
    private Object handleOf(long pid) throws Throwable {
        Optional<?> opt = (Optional<?>) mh_of.invoke(pid);
        return opt.orElse(null);
    }

    private static RuntimeException fail(String what, Throwable t) {
        return new RuntimeException("Failed to obtain " + what + " using ProcessHandle", t);
    }
}
