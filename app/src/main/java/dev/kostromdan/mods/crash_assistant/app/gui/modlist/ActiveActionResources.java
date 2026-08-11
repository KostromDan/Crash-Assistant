package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import java.io.InputStream;
import java.net.HttpURLConnection;

/** Keeps cancellation I/O bound to the action owner that created it. */
final class ActiveActionResources {
    private long generation;
    private Thread activeThread;
    private Object downloadOwner;
    private InputStream downloadStream;
    private HttpURLConnection downloadConnection;

    synchronized void begin(Thread thread) {
        generation++;
        activeThread = thread;
        downloadOwner = null;
        downloadStream = null;
        downloadConnection = null;
    }

    synchronized void finish(Thread thread) {
        if (activeThread != thread) return;
        activeThread = null;
        downloadOwner = null;
        downloadStream = null;
        downloadConnection = null;
    }

    synchronized boolean hasActiveThread() {
        return activeThread != null;
    }

    synchronized boolean isDownloadFor(Object owner) {
        return downloadOwner == owner && (downloadStream != null || downloadConnection != null);
    }

    synchronized void initializeDownload(Object owner) {
        downloadOwner = owner;
        downloadStream = null;
        downloadConnection = null;
    }

    synchronized void rememberDownload(Object owner, InputStream stream, HttpURLConnection connection) {
        downloadOwner = owner;
        downloadStream = stream;
        downloadConnection = connection;
    }

    synchronized void clearDownload(InputStream stream) {
        if (downloadStream != stream) return;
        downloadOwner = null;
        downloadStream = null;
        downloadConnection = null;
    }

    /** Interrupts the current owner before it can be replaced and snapshots only its I/O. */
    synchronized AbortRequest interruptAndSnapshot() {
        boolean hadActiveOwner = activeThread != null;
        if (hadActiveOwner) {
            try {
                activeThread.interrupt();
            } catch (Exception ignored) {
            }
        }
        return snapshotLocked(hadActiveOwner);
    }

    synchronized AbortRequest snapshotDownload(Object expectedOwner) {
        if (expectedOwner != null && downloadOwner != expectedOwner) {
            return new AbortRequest(this, generation, false, null, null);
        }
        return snapshotLocked(activeThread != null);
    }

    private AbortRequest snapshotLocked(boolean hadActiveOwner) {
        return new AbortRequest(this, generation, hadActiveOwner, downloadStream, downloadConnection);
    }

    private synchronized void clearIfMatching(long requestGeneration,
                                              InputStream stream,
                                              HttpURLConnection connection) {
        if (generation != requestGeneration) return;
        boolean clearedMatchingResource = false;
        if (downloadStream == stream && stream != null) {
            downloadStream = null;
            clearedMatchingResource = true;
        }
        if (downloadConnection == connection && connection != null) {
            downloadConnection = null;
            clearedMatchingResource = true;
        }
        if (clearedMatchingResource && downloadStream == null && downloadConnection == null) {
            downloadOwner = null;
        }
    }

    static final class AbortRequest {
        private final ActiveActionResources resources;
        private final long generation;
        private final boolean hadActiveOwner;
        private final InputStream stream;
        private final HttpURLConnection connection;

        private AbortRequest(ActiveActionResources resources,
                             long generation,
                             boolean hadActiveOwner,
                             InputStream stream,
                             HttpURLConnection connection) {
            this.resources = resources;
            this.generation = generation;
            this.hadActiveOwner = hadActiveOwner;
            this.stream = stream;
            this.connection = connection;
        }

        void abort() {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
            resources.clearIfMatching(generation, stream, connection);
        }

        boolean hadActiveOwner() {
            return hadActiveOwner;
        }
    }
}
