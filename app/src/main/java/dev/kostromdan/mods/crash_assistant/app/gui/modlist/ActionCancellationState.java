package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import java.util.function.BooleanSupplier;

/** Thread-safe cancellation flags shared by the action worker and Swing controls. */
final class ActionCancellationState {
    private volatile boolean currentRequested;
    private volatile boolean allRequested;

    synchronized void requestCurrent() {
        currentRequested = true;
    }

    /** Records Cancel Current only when its atomically captured owner is still active. */
    synchronized boolean requestCurrentAtomically(BooleanSupplier captureCurrentOwner) {
        if (captureCurrentOwner == null) {
            throw new IllegalArgumentException("captureCurrentOwner must not be null");
        }
        currentRequested = captureCurrentOwner.getAsBoolean();
        return currentRequested;
    }

    synchronized void requestAll() {
        allRequested = true;
        currentRequested = true;
    }

    /** Records Cancel All and captures owner-bound resources before that owner can finish. */
    synchronized void requestAllAtomically(Runnable captureBeforeOwnerCanFinish) {
        if (captureBeforeOwnerCanFinish == null) {
            throw new IllegalArgumentException("captureBeforeOwnerCanFinish must not be null");
        }
        allRequested = true;
        currentRequested = true;
        captureBeforeOwnerCanFinish.run();
    }

    boolean isCurrentRequested() {
        return currentRequested;
    }

    boolean isAllRequested() {
        return allRequested;
    }

    synchronized boolean consumeCurrentOnly() {
        if (!currentRequested || allRequested) {
            return false;
        }
        currentRequested = false;
        return true;
    }

    synchronized boolean clearAfterOwnerFinished(BooleanSupplier closing) {
        if (closing.getAsBoolean()) {
            return false;
        }
        boolean changed = currentRequested || allRequested;
        currentRequested = false;
        allRequested = false;
        return changed;
    }
}
