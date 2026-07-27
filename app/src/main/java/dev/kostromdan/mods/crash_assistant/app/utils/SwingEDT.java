package dev.kostromdan.mods.crash_assistant.app.utils;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

public final class SwingEDT {
    private SwingEDT() {
    }

    public static void runAndWait(Runnable action) {
        callAndWait(() -> {
            action.run();
            return null;
        });
    }

    public static void invokeAndWait(Runnable action) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeAndWait(action);
        }
    }

    public static <T> T callAndWait(Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.get();
        }

        FutureTask<T> task = new FutureTask<>(action::get);
        SwingUtilities.invokeLater(task);

        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return task.get();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } catch (ExecutionException e) {
            rethrow(e.getCause());
            return null;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }
}
