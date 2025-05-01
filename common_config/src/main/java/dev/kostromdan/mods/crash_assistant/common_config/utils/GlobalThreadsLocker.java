package dev.kostromdan.mods.crash_assistant.common_config.utils;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class that provides functionality to block all threads similar to a thread dump.
 * <p>
 * Usage example:
 * <pre>
 * GlobalThreadsLocker lock = new GlobalThreadsLocker();
 * try {
 *     lock.lock();
 *     // Perform operations while all threads are locked
 * } finally {
 *     lock.unlock();
 * }
 * </pre>
 */
public class GlobalThreadsLocker {
    private final ReentrantLock lock = new ReentrantLock();
    private final ThreadGroup rootThreadGroup;

    public GlobalThreadsLocker() {
        // Get the root thread group
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        this.rootThreadGroup = root;
    }

    /**
     * Locks all threads in the JVM.
     * This method suspends all threads except the current one.
     */
    public void lock() {
        lock.lock();
        Thread[] threads = getThreadsSnapshot();
        
        // Suspend all threads except the current one
        for (Thread thread : threads) {
            if (thread != Thread.currentThread() && thread.isAlive()) {
                try {
                    // Using the suspend method which is similar to how thread dumps work
                    // Note: This method is deprecated but still used internally for thread dumps
                    thread.suspend();
                } catch (Exception e) {
                    // Ignore any exceptions when trying to suspend threads
                }
            }
        }
    }

    /**
     * Unlocks all previously locked threads.
     * This method should always be called in a finally block after lock().
     */
    public void unlock() {
        try {
            Thread[] threads = getThreadsSnapshot();
            
            // Resume all threads that were suspended
            for (Thread thread : threads) {
                if (thread != Thread.currentThread() && thread.isAlive()) {
                    try {
                        // Resume the thread
                        thread.resume();
                    } catch (Exception e) {
                        // Ignore any exceptions when trying to resume threads
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private Thread[] getThreadsSnapshot() {
        // Get the approximate thread count
        int estimatedSize = rootThreadGroup.activeCount();
        Thread[] threads = new Thread[estimatedSize * 2]; // Double the size to be safe
        
        // Fill the array with threads
        int actualSize = rootThreadGroup.enumerate(threads, true);
        
        // If we filled the array completely, it's possible we missed some threads
        if (actualSize == threads.length) {
            return getThreadsSnapshot(); // Try again with a larger array
        }
        
        return threads;
    }
}
