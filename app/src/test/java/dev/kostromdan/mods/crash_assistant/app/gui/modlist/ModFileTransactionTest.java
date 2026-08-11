package dev.kostromdan.mods.crash_assistant.app.gui.modlist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModFileTransactionTest {
    private ModFileTransactionTest() {
    }

    public static void main(String[] args) throws Exception {
        testSuccessfulReplacementAndRemoval();
        testDeterministicFailureRollsBackAndCanBeRetried();
        testCancellationAfterFirstInstallationRollsBack();
        testDisabledSourceIsRemovedOnlyAtCommitAndRestoredOnFailure();
        testIncompleteRollbackBackupSurvivesTransientCleanup();
        testCancelAllCanOnlyBeClearedByOwner();
        testCancelAllQueueCleanupIsAtomicWithOwnerFinish();
        testDelayedAbortCannotAffectNextOwner();
        testManualCandidateIsCopiedIntoStaging();
        System.out.println("Mod file transaction tests passed.");
    }

    private static void testSuccessfulReplacementAndRemoval() throws Exception {
        Path root = Files.createTempDirectory("mod-file-transaction-success-");
        try {
            Path temporaryRoot = Files.createDirectories(root.resolve("transaction"));
            Path mods = Files.createDirectories(root.resolve("mods"));
            Path staging = Files.createDirectories(root.resolve("staging"));
            Path target = write(mods.resolve("example.jar"), "old");
            Path removed = write(mods.resolve("obsolete.jar"), "obsolete");
            Path prepared = write(staging.resolve("example.jar"), "new");

            ModFileTransaction.Result result = ModFileTransaction.apply(
                    temporaryRoot,
                    Collections.singletonList(removed),
                    Collections.<Path>emptyList(),
                    Collections.singletonList(new ModFileTransaction.Installation(prepared, target)),
                    () -> false);

            assertEquals("new", read(target), "replacement is committed");
            assertTrue(!Files.exists(removed), "removed file is committed");
            assertTrue(!Files.exists(prepared), "staged file was consumed");
            assertTrue(result.getCleanupFailure() == null, "committed backup was deleted");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testDeterministicFailureRollsBackAndCanBeRetried() throws Exception {
        Path root = Files.createTempDirectory("mod-file-transaction-failure-");
        try {
            Path temporaryRoot = Files.createDirectories(root.resolve("transaction"));
            Path mods = Files.createDirectories(root.resolve("mods"));
            Path staging = Files.createDirectories(root.resolve("staging"));
            Path firstTarget = write(mods.resolve("first.jar"), "first-old");
            Path removed = write(mods.resolve("removed.jar"), "removed-old");
            Path firstPrepared = write(staging.resolve("first.jar"), "first-new");
            Path secondPrepared = write(staging.resolve("second.jar"), "second-new");
            Path blockedParent = write(root.resolve("blocked-parent"), "not-a-directory");
            Path secondTarget = blockedParent.resolve("second.jar");

            ModFileTransaction.TransactionException failure = null;
            try {
                ModFileTransaction.apply(
                        temporaryRoot,
                        Collections.singletonList(removed),
                        Collections.<Path>emptyList(),
                        Arrays.asList(
                                new ModFileTransaction.Installation(firstPrepared, firstTarget),
                                new ModFileTransaction.Installation(secondPrepared, secondTarget)),
                        () -> false);
            } catch (ModFileTransaction.TransactionException expected) {
                failure = expected;
            }

            assertTrue(failure != null, "deterministic second-install failure is reported");
            assertTrue(failure.isRollbackComplete(), "failure rollback completed");
            assertEquals("first-old", read(firstTarget), "first replacement was rolled back");
            assertEquals("removed-old", read(removed), "removed file was restored");
            assertEquals("first-new", read(firstPrepared), "first staged file is available for cleanup/retry");
            assertEquals("second-new", read(secondPrepared), "unattempted staged file remains available");

            Files.delete(blockedParent);
            ModFileTransaction.apply(
                    temporaryRoot,
                    Collections.singletonList(removed),
                    Collections.<Path>emptyList(),
                    Arrays.asList(
                            new ModFileTransaction.Installation(firstPrepared, firstTarget),
                            new ModFileTransaction.Installation(secondPrepared, secondTarget)),
                    () -> false);

            assertEquals("first-new", read(firstTarget), "retry commits first replacement");
            assertEquals("second-new", read(secondTarget), "retry commits second replacement");
            assertTrue(!Files.exists(removed), "retry commits removal");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testCancellationAfterFirstInstallationRollsBack() throws Exception {
        Path root = Files.createTempDirectory("mod-file-transaction-cancel-");
        try {
            Path temporaryRoot = Files.createDirectories(root.resolve("transaction"));
            Path mods = Files.createDirectories(root.resolve("mods"));
            Path staging = Files.createDirectories(root.resolve("staging"));
            Path firstTarget = write(mods.resolve("first.jar"), "first-old");
            Path secondTarget = write(mods.resolve("second.jar"), "second-old");
            Path firstPrepared = write(staging.resolve("first.jar"), "first-new");
            Path secondPrepared = write(staging.resolve("second.jar"), "second-new");
            AtomicInteger checkpoints = new AtomicInteger();

            ModFileTransaction.CancelledException cancellation = null;
            try {
                ModFileTransaction.apply(
                        temporaryRoot,
                        Collections.<Path>emptyList(),
                        Collections.<Path>emptyList(),
                        Arrays.asList(
                                new ModFileTransaction.Installation(firstPrepared, firstTarget),
                                new ModFileTransaction.Installation(secondPrepared, secondTarget)),
                        () -> checkpoints.incrementAndGet() >= 4);
            } catch (ModFileTransaction.CancelledException expected) {
                cancellation = expected;
            }

            assertTrue(cancellation != null, "cancellation is reported");
            assertTrue(cancellation.isRollbackComplete(), "cancellation rollback completed");
            assertEquals("first-old", read(firstTarget), "first target restored after cancellation");
            assertEquals("second-old", read(secondTarget), "second target remains unchanged after cancellation");
            assertEquals("first-new", read(firstPrepared), "installed staged file returned after cancellation");
            assertEquals("second-new", read(secondPrepared), "pending staged file remains after cancellation");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testDisabledSourceIsRemovedOnlyAtCommitAndRestoredOnFailure() throws Exception {
        Path root = Files.createTempDirectory("mod-file-transaction-disabled-");
        try {
            Path temporaryRoot = Files.createDirectories(root.resolve("transaction"));
            Path mods = Files.createDirectories(root.resolve("mods"));
            Path staging = Files.createDirectories(root.resolve("staging"));
            Path disabledSource = write(mods.resolve("example.jar.disabled"), "saved-version");
            Path prepared = write(staging.resolve("example.jar"), "saved-version");
            Path enabledTarget = mods.resolve("example.jar");
            Path blockedParent = write(root.resolve("blocked-parent"), "not-a-directory");
            Path secondPrepared = write(staging.resolve("second.jar"), "second");

            assertEquals("saved-version", read(disabledSource), "disabled source is untouched during staging");
            try {
                ModFileTransaction.apply(
                        temporaryRoot,
                        Collections.<Path>emptyList(),
                        Collections.<Path>emptyList(),
                        Arrays.asList(
                                new ModFileTransaction.Installation(prepared, enabledTarget, disabledSource),
                                new ModFileTransaction.Installation(secondPrepared, blockedParent.resolve("second.jar"))),
                        () -> false);
                throw new AssertionError("disabled-source transaction should fail deterministically");
            } catch (ModFileTransaction.TransactionException expected) {
                assertTrue(expected.isRollbackComplete(), "disabled-source rollback completed");
            }

            assertEquals("saved-version", read(disabledSource), "disabled source restored on failure");
            assertTrue(!Files.exists(enabledTarget), "enabled copy is removed on rollback");
            assertEquals("saved-version", read(prepared), "prepared copy returned on rollback");

            Files.delete(blockedParent);
            ModFileTransaction.apply(
                    temporaryRoot,
                    Collections.<Path>emptyList(),
                    Collections.<Path>emptyList(),
                    Arrays.asList(
                            new ModFileTransaction.Installation(prepared, enabledTarget, disabledSource),
                            new ModFileTransaction.Installation(secondPrepared, blockedParent.resolve("second.jar"))),
                    () -> false);
            assertTrue(!Files.exists(disabledSource), "successful commit removes the disabled source");
            assertEquals("saved-version", read(enabledTarget), "successful commit leaves only the enabled file");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testIncompleteRollbackBackupSurvivesTransientCleanup() throws Exception {
        Path root = Files.createTempDirectory("mod-file-transaction-recovery-");
        try {
            Path transientRoot = Files.createDirectories(root.resolve("mods").resolve(".crash_assistant_tmp"));
            Path recoveryRoot = Files.createDirectories(root.resolve("local").resolve("crash_assistant")
                    .resolve("mod_file_recovery"));
            Path mods = Files.createDirectories(root.resolve("mods-live"));
            Path staging = Files.createDirectories(transientRoot.resolve("action"));
            Path target = write(mods.resolve("example.jar"), "old-version");
            Path prepared = write(staging.resolve("example.jar"), "new-version");
            AtomicInteger checkpoints = new AtomicInteger();

            ModFileTransaction.CancelledException cancellation = null;
            try {
                ModFileTransaction.apply(
                        recoveryRoot,
                        Collections.<Path>emptyList(),
                        Collections.<Path>emptyList(),
                        Collections.singletonList(new ModFileTransaction.Installation(prepared, target)),
                        () -> {
                            if (checkpoints.incrementAndGet() != 3) return false;
                            try {
                                // Make both rollback moves fail deterministically. The original backup
                                // must remain in the persistent recovery root for manual restoration.
                                Files.delete(target);
                                Files.createDirectories(target);
                                write(target.resolve("blocker"), "block target replacement");
                                Files.createDirectories(prepared);
                                write(prepared.resolve("blocker"), "block staged-file restoration");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            return true;
                        });
            } catch (ModFileTransaction.CancelledException expected) {
                cancellation = expected;
            }

            assertTrue(cancellation != null, "incomplete rollback is reported");
            assertTrue(!cancellation.isRollbackComplete(), "rollback failure is distinguished from clean cancel");
            Path backupDirectory = cancellation.getBackupDirectory();
            assertTrue(backupDirectory != null && backupDirectory.startsWith(recoveryRoot),
                    "recovery backup is created under the persistent root");
            assertTrue(containsFileWithContent(backupDirectory, "old-version"),
                    "recovery backup retains the original mod");

            deleteRecursively(transientRoot);
            assertTrue(Files.isDirectory(backupDirectory),
                    "normal transient-download cleanup cannot remove the recovery backup");
            assertTrue(containsFileWithContent(backupDirectory, "old-version"),
                    "original mod remains recoverable after transient cleanup");

            Path productionTransient = ModListDiffDialog.tmpDownloadsFolder.toAbsolutePath().normalize();
            Path productionRecovery = ModListDiffDialog.getTransactionRecoveryFolder().toAbsolutePath().normalize();
            assertTrue(!productionRecovery.startsWith(productionTransient),
                    "production recovery root is outside the GUI auto-cleanup folder");
        } finally {
            deleteRecursively(root);
        }
    }

    private static void testCancelAllCanOnlyBeClearedByOwner() {
        ActionCancellationState state = new ActionCancellationState();

        state.requestAll();
        assertTrue(state.isAllRequested(), "Cancel All is recorded");
        assertTrue(state.isCurrentRequested(), "Cancel All also cancels the active entry");
        assertTrue(!state.consumeCurrentOnly(), "single-entry cleanup cannot consume Cancel All");
        assertTrue(state.isAllRequested(), "Cancel All survives single-entry cleanup");

        state.clearAfterOwnerFinished(() -> false);
        assertTrue(!state.isAllRequested(), "batch owner clears Cancel All after leaving its loop");
        assertTrue(!state.isCurrentRequested(), "batch owner clears the associated current cancellation");

        state.requestCurrent();
        assertTrue(state.consumeCurrentOnly(), "a real single cancellation is consumed independently");
        assertTrue(!state.isCurrentRequested(), "single cancellation does not leak to the next entry");
        assertTrue(!state.requestCurrentAtomically(() -> false),
                "Cancel Current is ignored when no action owner was captured");
        assertTrue(!state.isCurrentRequested(), "an idle Cancel Current cannot poison the next owner");

        state.requestAll();
        state.clearAfterOwnerFinished(() -> true);
        assertTrue(state.isAllRequested(), "closing keeps cancellation asserted for rollback and shutdown");
    }

    private static void testCancelAllQueueCleanupIsAtomicWithOwnerFinish() throws Exception {
        ActionCancellationState state = new ActionCancellationState();
        CountDownLatch ownerAttemptedClear = new CountDownLatch(1);
        AtomicBoolean cleanupCompleted = new AtomicBoolean(false);
        AtomicBoolean ownerObservedCleanup = new AtomicBoolean(false);
        Thread[] ownerRef = new Thread[1];

        state.requestAllAtomically(() -> {
            Thread owner = new Thread(() -> {
                ownerAttemptedClear.countDown();
                state.clearAfterOwnerFinished(() -> false);
                ownerObservedCleanup.set(cleanupCompleted.get());
            }, "cancel-all-owner-finish-test");
            ownerRef[0] = owner;
            owner.start();

            await(ownerAttemptedClear, "owner reached cancellation cleanup");
            awaitBlocked(owner, "owner cannot clear Cancel All before queue cleanup completes");
            assertTrue(state.isAllRequested(), "Cancel All remains asserted throughout queue cleanup");
            cleanupCompleted.set(true);
        });

        Thread owner = ownerRef[0];
        owner.join(TimeUnit.SECONDS.toMillis(5));
        assertTrue(!owner.isAlive(), "owner completes after atomic queue cleanup releases the monitor");
        assertTrue(ownerObservedCleanup.get(), "owner clears cancellation only after queue cleanup completed");
        assertTrue(!state.isAllRequested(), "owner clears Cancel All after the atomic cleanup section");
    }

    private static void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError(label + ": timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(label + ": interrupted", e);
        }
    }

    private static void awaitBlocked(Thread thread, String label) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State threadState = thread.getState();
            if (threadState == Thread.State.BLOCKED) {
                return;
            }
            if (threadState == Thread.State.TERMINATED) {
                throw new AssertionError(label + ": owner finished before cleanup callback returned");
            }
            Thread.yield();
        }
        throw new AssertionError(label + ": owner did not block on the cancellation-state monitor");
    }

    private static void testDelayedAbortCannotAffectNextOwner() {
        ActiveActionResources resources = new ActiveActionResources();
        Thread reusedExecutorThread = Thread.currentThread();
        Object retriedEntry = new Object();
        TrackingInputStream oldDownload = new TrackingInputStream();
        TrackingInputStream newDownload = new TrackingInputStream();

        resources.begin(reusedExecutorThread);
        resources.rememberDownload(retriedEntry, oldDownload, null);
        ActiveActionResources.AbortRequest delayedAbort = resources.interruptAndSnapshot();
        assertTrue(delayedAbort.hadActiveOwner(), "cancellation captures the active owner");
        assertTrue(Thread.interrupted(), "the active owner is interrupted immediately at cancellation time");

        resources.finish(reusedExecutorThread);
        resources.begin(reusedExecutorThread);
        resources.rememberDownload(retriedEntry, newDownload, null);

        delayedAbort.abort();

        assertTrue(oldDownload.closed, "delayed cleanup closes the cancelled owner's download");
        assertTrue(!newDownload.closed, "delayed cleanup cannot close the next owner's download");
        assertTrue(resources.isDownloadFor(retriedEntry),
                "delayed cleanup cannot clear the next owner's tracking state");
        assertTrue(!Thread.currentThread().isInterrupted(),
                "delayed cleanup does not interrupt the executor thread after it was reused");
        resources.finish(reusedExecutorThread);
    }

    private static final class TrackingInputStream extends InputStream {
        private volatile boolean closed;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    private static void testManualCandidateIsCopiedIntoStaging() throws Exception {
        Path root = Files.createTempDirectory("manual-download-staging-");
        try {
            Path candidate = write(root.resolve("user-selected.jar"), "downloaded-mod");
            Path staging = root.resolve("staging");

            Path staged = ManualDownloadDialog.copyAcceptedCandidateToStaging(
                    candidate, staging, "expected-name.jar");

            assertEquals("downloaded-mod", read(staged), "manual candidate is copied to staging");
            assertEquals("downloaded-mod", read(candidate), "manual candidate remains at its user-owned location");
            Files.delete(staged);
            assertEquals("downloaded-mod", read(candidate), "staging cleanup cannot delete the user's file");
        } finally {
            deleteRecursively(root);
        }
    }

    private static Path write(Path path, String value) throws Exception {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static boolean containsFileWithContent(Path root, String expected) throws Exception {
        if (root == null || !Files.exists(root)) return false;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            Path[] files = paths.filter(Files::isRegularFile).toArray(Path[]::new);
            for (Path file : files) {
                if (expected.equals(read(file))) return true;
            }
        }
        return false;
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
