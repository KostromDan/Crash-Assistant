package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reflection-based {@link ProcessHelperImpl} that calls
 * {@code java.lang.ProcessHandle} only when it is present (JDK 9+).
 * <p>
 * All reflective lookups are performed once in the constructor and cached
 * as {@link MethodHandle}s, so the per-call overhead is negligible.
 * The class still compiles on Java 8 because it never references
 * {@code ProcessHandle} at compile time.
 */
@SuppressWarnings({"unchecked", "OptionalUsedAsFieldOrParameterType"})
public final class ProcessHelperProcessHandleImpl implements ProcessHelperImpl {
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

    public ProcessHelperProcessHandleImpl() {
        try {
            Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
            Class<?> processHandleInfoClass = Class.forName("java.lang.ProcessHandle$Info");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            mh_current = lookup.findStatic(processHandleClass, "current",
                    MethodType.methodType(processHandleClass));
            mh_of = lookup.findStatic(processHandleClass, "of",
                    MethodType.methodType(Optional.class, long.class));
            mh_pid = lookup.findVirtual(processHandleClass, "pid",
                    MethodType.methodType(long.class));
            mh_info = lookup.findVirtual(processHandleClass, "info",
                    MethodType.methodType(processHandleInfoClass));
            mh_command = lookup.findVirtual(processHandleInfoClass, "command",
                    MethodType.methodType(Optional.class));
            mh_startInstant = lookup.findVirtual(processHandleInfoClass, "startInstant",
                    MethodType.methodType(Optional.class));
            mh_isAlive = lookup.findVirtual(processHandleClass, "isAlive",
                    MethodType.methodType(boolean.class));
            mh_children = lookup.findVirtual(processHandleClass, "children",
                    MethodType.methodType(Stream.class));
            mh_destroy = lookup.findVirtual(processHandleClass, "destroy",
                    MethodType.methodType(boolean.class));
            mh_destroyForcibly = lookup.findVirtual(processHandleClass, "destroyForcibly",
                    MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ProcessHandle API not found on a Java 9+ runtime", e);
        }
    }

    @Override
    public long getCurrentProcessId() {
        try {
            Object processHandle = mh_current.invoke();
            return (long) mh_pid.invoke(processHandle);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public Optional<String> getCurrentProcessCommand() {
        try {
            Object processHandle = mh_current.invoke();
            Object info = mh_info.invoke(processHandle);
            return (Optional<String>) mh_command.invoke(info);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public long getCurrentProcessStartTime() {
        try {
            Object processHandle = mh_current.invoke();
            Object info = mh_info.invoke(processHandle);
            Optional<Instant> startInstant = (Optional<Instant>) mh_startInstant.invoke(info);
            return startInstant.map(Instant::toEpochMilli).orElse(-1L);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public long getProcessStartTime(long pid) {
        try {
            Object processHandle = handleOf(pid);
            if (processHandle == null) {
                return -1L;
            }

            Object info = mh_info.invoke(processHandle);
            Optional<Instant> startInstant = (Optional<Instant>) mh_startInstant.invoke(info);
            return startInstant.map(Instant::toEpochMilli).orElse(-1L);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public boolean isProcessAlive(long pid) {
        try {
            Object processHandle = handleOf(pid);
            return processHandle != null && (boolean) mh_isAlive.invoke(processHandle);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public String getChildProcessesInfo() {
        try {
            Object currentProcessHandle = mh_current.invoke();
            Stream<?> children = (Stream<?>) mh_children.invoke(currentProcessHandle);

            try {
                return children
                        .map(this::formatChildProcessInfo)
                        .collect(Collectors.joining("\n"));
            } finally {
                children.close();
            }
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public boolean destroyProcess(long pid) {
        try {
            Object processHandle = handleOf(pid);
            if (processHandle == null) {
                return false;
            }
            return (boolean) mh_destroy.invoke(processHandle);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    @Override
    public boolean destroyProcessForcibly(long pid) {
        try {
            Object processHandle = handleOf(pid);
            if (processHandle == null) {
                return false;
            }
            return (boolean) mh_destroyForcibly.invoke(processHandle);
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    private Object handleOf(long pid) throws Throwable {
        Optional<?> optionalHandle = (Optional<?>) mh_of.invoke(pid);
        return optionalHandle.orElse(null);
    }

    private String formatChildProcessInfo(Object processHandle) {
        try {
            long childPid = (long) mh_pid.invoke(processHandle);
            Object info = mh_info.invoke(processHandle);
            Optional<Instant> startInstant = (Optional<Instant>) mh_startInstant.invoke(info);
            return childPid + ": " + startInstant.get().toEpochMilli();
        } catch (Throwable t) {
            throw rethrowUnchecked(t);
        }
    }

    private static RuntimeException rethrowUnchecked(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new AssertionError("Unexpected checked throwable from ProcessHandle MethodHandle invocation", t);
    }
}