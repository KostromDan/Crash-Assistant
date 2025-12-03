package dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api.CurseForge;
import dev.kostromdan.mods.crash_assistant.app.utils.mods_downloader.api.Modrinth;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Aggregates platform lookups for Modrinth + CurseForge in a single place.
 * This ensures we only perform at most two HTTP requests per dialog open.
 */
public class ModPlatformLookupService {

    public static class LookupResult {
        private final Map<Long, CurseForge.FingerprintMatch> curseForgeMatches;
        private final Map<String, Modrinth.VersionFileInfo> modrinthMatches;

        public LookupResult(Map<Long, CurseForge.FingerprintMatch> curseForgeMatches,
                            Map<String, Modrinth.VersionFileInfo> modrinthMatches) {
            this.curseForgeMatches = curseForgeMatches == null ? Collections.<Long, CurseForge.FingerprintMatch>emptyMap() : curseForgeMatches;
            this.modrinthMatches = modrinthMatches == null ? Collections.<String, Modrinth.VersionFileInfo>emptyMap() : modrinthMatches;
        }

        public Map<Long, CurseForge.FingerprintMatch> getCurseForgeMatches() {
            return curseForgeMatches;
        }

        public Map<String, Modrinth.VersionFileInfo> getModrinthMatches() {
            return modrinthMatches;
        }
    }

    public LookupResult lookup(Set<Long> curseForgeFingerprints, Set<String> modrinthFingerprints) {
        Map<Long, CurseForge.FingerprintMatch> cf = new HashMap<Long, CurseForge.FingerprintMatch>();
        Map<String, Modrinth.VersionFileInfo> mr = new HashMap<String, Modrinth.VersionFileInfo>();

        boolean needCf = curseForgeFingerprints != null && !curseForgeFingerprints.isEmpty();
        boolean needMr = modrinthFingerprints != null && !modrinthFingerprints.isEmpty();

        if (needCf && needMr) {
            CompletableFuture<Void> cfFuture = CompletableFuture.runAsync(() ->
                    cf.putAll(runWithRetry(() -> CurseForge.lookupFingerprints(curseForgeFingerprints))));
            CompletableFuture<Void> mrFuture = CompletableFuture.runAsync(() ->
                    mr.putAll(runWithRetry(() -> Modrinth.lookupVersionFiles(modrinthFingerprints))));
            CompletableFuture.allOf(cfFuture, mrFuture).join();
        } else {
            if (needCf) {
                cf.putAll(runWithRetry(() -> CurseForge.lookupFingerprints(curseForgeFingerprints)));
            }
            if (needMr) {
                mr.putAll(runWithRetry(() -> Modrinth.lookupVersionFiles(modrinthFingerprints)));
            }
        }

        return new LookupResult(cf, mr);
    }

    private <T> T runWithRetry(Callable<T> task) {
        int attempts = 0;
        while (true) {
            try {
                return task.call();
            } catch (IOException ioe) {
                attempts++;
                CrashAssistantApp.LOGGER.warn("Lookup attempt {} failed due to connection issue, retrying...", attempts, ioe);
                try {
                    TimeUnit.SECONDS.sleep(Math.min(5, 1 + attempts));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Lookup interrupted while retrying", ie);
                }
            } catch (Exception e) {
                throw new RuntimeException("Lookup failed", e);
            }
        }
    }
}
