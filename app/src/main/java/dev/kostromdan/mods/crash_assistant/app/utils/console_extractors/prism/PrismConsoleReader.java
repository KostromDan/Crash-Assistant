package dev.kostromdan.mods.crash_assistant.app.utils.console_extractors.prism;

import com.sun.jna.IntegerType;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.*;

/**
 * PrismConsoleReader
 * ------------------------------------------------------------------------------------
 * Purpose
 *  - Prism Launcher does not persist the in‑app console (stdout/stderr) to a file.
 *    To obtain an exact copy of what Prism shows in its console pane, we read
 *    Prism's process memory directly and reconstruct the Qt LogModel ring buffer.
 *
 * High‑level algorithm (updated)
 *  1) Find the Prism process (PrismLauncher.exe / prismlauncher.exe) via Toolhelp32.
 *  2) Enforce architecture compatibility (require 64‑bit JVM; reject WOW64 targets).
 *  3) Enumerate readable, committed, private memory regions (VirtualQueryEx).
 *  4) **Initial anchor**: scan only for
 *         "Launching CrashAssistantApp (CrashAssistant-fabric-1.19-1.21.4-1.10.19.jar)"
 *  5) For each anchor, infer plausible QString layouts, then find owners and probe
 *     entry layouts with pointer offsets {0,8,16,24} and strides {16,24,32,40}.
 *  6) For each viable layout, try exact ring reconstruction; if that fails, try a
 *     contiguous linear block **allowing empty lines**.
 *  7) **Validation (new)**: return the **first** candidate whose reconstructed text
 *     contains **all** required markers:
 *     "Java Arguments:", "Launching CrashAssistantApp (CrashAssistant-fabric-1.19-1.21.4-1.10.19.jar)",
 *     "Prism Launcher version:", "Minecraft folder is:", "Java path is:", "Libraries:",
 *     "Minecraft process ID:".
 *
 * Notes
 *  - No disk fallbacks (no latest.log), no external commands.
 *  - JNA core only (local Win32 types) to avoid jna/jna‑platform ABI mismatches.
 *  - Java 8 compatible: no streams/lambdas.
 *
 * Public API
 *  - String PrismConsoleReader.getPrismConsoleText()
 *    Returns the console text or null if the model cannot be reliably located
 *    or if no candidate passes strict validation.
 */
public final class PrismConsoleReader {

    private PrismConsoleReader() {}

    // ---- diagnostics ----
    private static final boolean LOG = true;
    private static void log(String fmt, Object... args) {
        if (!LOG) return;
        if (args == null || args.length == 0) {
            System.err.println("[PrismConsoleReader] " + fmt);
        } else {
            System.err.println("[PrismConsoleReader] " + String.format(Locale.ROOT, fmt, args));
        }
    }

    // ---- Win32 constants (local) ----
    private static final int PROCESS_VM_READ           = 0x0010;
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;
    private static final int ACCESS = PROCESS_VM_READ | PROCESS_QUERY_INFORMATION;

    private static final int TH32CS_SNAPPROCESS = 0x00000002;
    private static final int MAX_PATH = 260;

    private static final int MEM_COMMIT  = 0x1000;
    private static final int MEM_PRIVATE = 0x20000;

    private static final int PAGE_NOACCESS            = 0x01;
    private static final int PAGE_READONLY            = 0x02;
    private static final int PAGE_READWRITE           = 0x04;
    private static final int PAGE_WRITECOPY           = 0x08;
    private static final int PAGE_EXECUTE_READ        = 0x20;
    private static final int PAGE_EXECUTE_READWRITE   = 0x40;
    private static final int PAGE_EXECUTE_WRITECOPY   = 0x80;
    private static final int PAGE_GUARD               = 0x100;

    private static final Set<Integer> READABLE_PROT = new HashSet<Integer>(Arrays.asList(
            PAGE_READONLY, PAGE_READWRITE, PAGE_WRITECOPY,
            PAGE_EXECUTE_READ, PAGE_EXECUTE_READWRITE, PAGE_EXECUTE_WRITECOPY
    ));

    // Executable names (case-insensitive)
    private static final String[] PRISM_EXE_NAMES = { "prismlauncher.exe" };

    // ---- Anchors & Validation (updated) ----

    /** Primary anchor to seed candidates (as required). */
    private static final String PRIMARY_ANCHOR =
            "Launching CrashAssistantApp (CrashAssistant-fabric-1.19-1.21.4-1.10.19.jar)";

    /** All markers that MUST be present in the final reconstruction. */
    private static final String[] REQUIRED_MARKERS = new String[] {
            "Java Arguments:",
            "Launching CrashAssistantApp (CrashAssistant-fabric-1.19-1.21.4-1.10.19.jar)",
            "Prism Launcher version:",
            "Minecraft folder is:",
            "Java path is:",
            "Libraries:",
            "Minecraft process ID:"
    };

    // (legacy anchors retained but unused; kept for reference)
    @SuppressWarnings("unused")
    private static final String[] LEGACY_LOG_ANCHORS = { "Minecraft folder is:", "Java Arguments:" };

    // Scan budgets
    private static final long GLOBAL_READ_BUDGET = 512L * 1024 * 1024; // 512 MB
    private static final int  REGION_READ_SLICE  = 2 * 1024 * 1024;    // 2 MB

    // QString charData delta probes (d + delta == char16_t* data)
    private static final int[] QSTRING_DATA_DELTAS = new int[] {
            8, 16, 24, 32, 40, 48, 56, 64, 72, 80, 88, 96
    };

    // Candidate entry layouts
    private static final int[] PTR_OFFSETS = new int[] { 0, 8, 16, 24 };
    private static final int[] STRIDES     = new int[] { 16, 24, 32, 40 };

    // ---- Public API ----

    public static String getPrismConsoleText() {
        log("Starting extraction...");

        int pid = findPrismPid();
        if (pid <= 0) {
            log("Prism process not found.");
            return null;
        }
        log("Found Prism PID: %d", Integer.valueOf(pid));

        HANDLE hProcess = K32.INSTANCE.OpenProcess(ACCESS, false, pid);
        if (hProcess == null || handlesEqual(hProcess, INVALID_HANDLE_VALUE)) {
            log("OpenProcess failed or returned INVALID_HANDLE_VALUE.");
            return null;
        }

        try {
            if (!ensureBitnessIsCompatible(hProcess)) {
                log("Bitness incompatible (require 64-bit JVM and non-WOW64 target).");
                return null;
            }

            // Enumerate regions once and reuse for pointer searches.
            List<MemoryRegion> regions = iterateReadablePrivateRegions(hProcess);
            log("Readable private regions: %d", Integer.valueOf(regions.size()));

            // --- NEW: initial candidates by the required primary anchor only ---
            List<AnchorHit> anchors = findAnchorUtf16Buffers(hProcess, regions, new String[]{ PRIMARY_ANCHOR });
            log("Anchors found (primary): %d", Integer.valueOf(anchors.size()));
            if (anchors.isEmpty()) {
                log("Primary anchor not found; giving up.");
                return null;
            }

            // Iterate over candidates; return the first that passes strict validation.
            for (int ai = 0; ai < anchors.size(); ai++) {
                AnchorHit a = anchors.get(ai);
                log("  Anchor \"%s\" @ 0x%016X", a.anchorText, Long.valueOf(a.charDataVA));

                List<QStringDescriptor> qdescs = enumerateQStringCandidates(hProcess, a.charDataVA, a.anchorText);
                log("  Validated QString candidates: %d", Integer.valueOf(qdescs.size()));
                if (qdescs.isEmpty()) continue;

                for (int qi = 0; qi < qdescs.size(); qi++) {
                    QStringDescriptor qd = qdescs.get(qi);
                    int delta = (int)(qd.charDataVA - qd.dPointer);
                    log("    QString d=0x%016X Δ=%d", Long.valueOf(qd.dPointer), Integer.valueOf(delta));

                    // Reuse enumerated regions to find owners efficiently
                    List<Long> owners = findPointerOccurrences(hProcess, regions, qd.dPointer, Native.POINTER_SIZE, 1024);
                    log("      Owners referencing d-pointer: %d", Integer.valueOf(owners.size()));
                    if (owners.isEmpty()) continue;

                    EntryArrayCandidate best = null;

                    for (int oi = 0; oi < owners.size(); oi++) {
                        long site = owners.get(oi);
                        for (int po = 0; po < PTR_OFFSETS.length; po++) {
                            int ptrOff = PTR_OFFSETS[po];
                            long entryVA = site - ptrOff;

                            for (int si = 0; si < STRIDES.length; si++) {
                                int stride = STRIDES[si];

                                String s0 = readEntryLine(hProcess, entryVA, ptrOff, qd.charDataVA - qd.dPointer);
                                if (!isPrintableOrEmpty(s0)) continue;

                                BlockSpan span = expandContiguousBlock(hProcess, entryVA, ptrOff, stride,
                                        qd.charDataVA - qd.dPointer, 4096);
                                if (span == null || span.count < 8) continue;

                                int ok = sampleDecode(hProcess, span.firstEntryVA, ptrOff, stride,
                                        qd.charDataVA - qd.dPointer, Math.min(span.count, 24));
                                if (ok == 0) continue;

                                if (best == null || span.count > best.span) {
                                    best = new EntryArrayCandidate(span.firstEntryVA, stride, ptrOff,
                                            span.count, qd.charDataVA - qd.dPointer);
                                }
                            }
                        }
                    }

                    if (best == null) {
                        log("      No contiguous entry block ≥ 8 found around owners.");
                        continue;
                    }

                    log("      Entry block: base=0x%016X stride=%d count=%d ptrOff=%d",
                            Long.valueOf(best.baseVA), Integer.valueOf(best.stride),
                            Integer.valueOf(best.span), Integer.valueOf(best.ptrOffset));

                    // Try exact ring reconstruction first
                    String exact = tryReconstructRingExactly(hProcess, regions, best);
                    if (exact != null && exact.length() > 0) {
                        if (containsAllRequiredMarkers(exact)) {
                            log("      Exact ring reconstruction succeeded and validated.");
                            return exact;
                        } else {
                            log("      Exact ring reconstruction failed validation; missing required markers.");
                        }
                    } else {
                        log("      Exact ring reconstruction unavailable or empty; will try linear block.");
                    }

                    // Fallback to RAM-only linear reconstruction (tolerant to empty lines)
                    String block = reconstructLinearBlock(hProcess, best);
                    if (block != null && block.length() > 0) {
                        if (containsAllRequiredMarkers(block)) {
                            log("      Using linear block reconstruction; validation passed.");
                            return block;
                        } else {
                            log("      Linear block reconstruction failed validation; missing required markers.");
                        }
                    } else {
                        log("      Contiguous block reconstruction failed.");
                    }
                }
            }

            log("All anchors/candidates exhausted; no candidate passed strict validation.");
            return null;
        } finally {
            K32.INSTANCE.CloseHandle(hProcess);
        }
    }

    // Optional local test
    public static void main(String[] args) {
        String s = getPrismConsoleText();
        log(s == null ? "<null>" : s.trim());
    }

    // ---- Validation helpers (NEW) ----

    private static boolean containsAllRequiredMarkers(String text) {
        if (text == null || text.length() == 0) return false;
        for (int i = 0; i < REQUIRED_MARKERS.length; i++) {
            String m = REQUIRED_MARKERS[i];
            if (m == null || m.length() == 0) continue;
            if (text.indexOf(m) < 0) {
                log("        Missing marker: \"%s\"", m);
                return false;
            }
        }
        return true;
    }

    // ---- Process discovery ----

    private static int findPrismPid() {
        HANDLE snapshot = K32.INSTANCE.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (snapshot == null || handlesEqual(snapshot, INVALID_HANDLE_VALUE)) return -1;

        try {
            PROCESSENTRY32W pe = new PROCESSENTRY32W();
            pe.dwSize.setValue(pe.size());
            pe.write();

            if (!K32.INSTANCE.Process32FirstW(snapshot, pe)) return -1;

            do {
                String exe = fromWide(pe.szExeFile);
                if (exe != null) {
                    for (int i = 0; i < PRISM_EXE_NAMES.length; i++) {
                        if (exe.equalsIgnoreCase(PRISM_EXE_NAMES[i])) {
                            return (int) pe.th32ProcessID.longValue();
                        }
                    }
                }
            } while (K32.INSTANCE.Process32NextW(snapshot, pe));
            return -1;
        } finally {
            K32.INSTANCE.CloseHandle(snapshot);
        }
    }

    private static String fromWide(char[] arr) {
        int n = 0;
        while (n < arr.length && arr[n] != 0) n++;
        return n == 0 ? "" : new String(arr, 0, n);
    }

    private static boolean handlesEqual(HANDLE a, HANDLE b) {
        if (a == null || b == null) return false;
        return Pointer.nativeValue(a.getPointer()) == Pointer.nativeValue(b.getPointer());
    }

    // ---- Bitness ----

    private static boolean ensureBitnessIsCompatible(HANDLE hProcess) {
        if (Native.POINTER_SIZE != 8) return false; // require 64-bit JVM

        IntByReference wow = new IntByReference(0);
        if (K32.INSTANCE.IsWow64Process(hProcess, wow) && wow.getValue() != 0) {
            return false; // 32-bit target on 64-bit OS (WOW64)
        }
        return true;
    }

    // ---- Anchors ----

    private static List<AnchorHit> findAnchorUtf16Buffers(HANDLE h, List<MemoryRegion> regions, String[] anchors) {
        List<AnchorHit> out = new ArrayList<AnchorHit>();
        if (anchors == null || anchors.length == 0) return out;

        long budget = GLOBAL_READ_BUDGET;

        for (int rgi = 0; rgi < regions.size(); rgi++) {
            if (budget <= 0) break;

            MemoryRegion r = regions.get(rgi);
            long remain = Math.min(r.size, budget);
            long off = 0;

            while (off < remain) {
                int readSize = (int) Math.min(REGION_READ_SLICE, remain - off);
                byte[] data = readBytes(h, r.base + off, readSize);
                if (data == null || data.length == 0) break;

                for (int a = 0; a < anchors.length; a++) {
                    String anchor = anchors[a];
                    int idx = indexOfUtf16LE(data, anchor);
                    if (idx >= 0) {
                        long addr = r.base + off + idx;
                        out.add(new AnchorHit(anchor, addr));
                        log("  Anchor \"%s\" hit at 0x%016X", anchor, Long.valueOf(addr));
                    }
                }

                off += data.length;
                budget -= data.length;
                if (budget <= 0) break;
            }
        }
        return out;
    }

    private static int indexOfUtf16LE(byte[] hay, String needle) {
        char[] cs = needle.toCharArray();
        byte[] pat = new byte[cs.length * 2];
        for (int i = 0; i < cs.length; i++) {
            pat[2 * i]     = (byte) (cs[i] & 0xFF);
            pat[2 * i + 1] = (byte) ((cs[i] >> 8) & 0xFF);
        }
        outer:
        for (int i = 0; i + pat.length <= hay.length; i += 2) {
            for (int j = 0; j < pat.length; j++) {
                if (hay[i + j] != pat[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static List<QStringDescriptor> enumerateQStringCandidates(HANDLE h, long charDataVA, String mustContain) {
        List<QStringDescriptor> out = new ArrayList<QStringDescriptor>();
        for (int di = 0; di < QSTRING_DATA_DELTAS.length; di++) {
            int delta = QSTRING_DATA_DELTAS[di];
            long d = charDataVA - delta;
            if (!isReadable(h, d, 16)) continue;

            String s = readUtf16StringUnsafe(h, d + delta, 2048);
            if (s != null && s.length() > 0 && s.indexOf(mustContain) >= 0) {
                out.add(new QStringDescriptor(d, charDataVA, (long) s.length()));
                log("    Validated QString: d=0x%016X Δ=%d textPrefix=\"%s\"",
                        Long.valueOf(d), Integer.valueOf(delta),
                        s.substring(0, Math.min(32, s.length())));
            }
        }
        return out;
    }

    // ---- Pointer search ----
    // Overload that reuses pre-enumerated regions for efficiency.
    private static List<Long> findPointerOccurrences(HANDLE h, List<MemoryRegion> regions, long value, int alignment, int maxResults) {
        List<Long> out = new ArrayList<Long>();
        long budget = GLOBAL_READ_BUDGET / 2;

        int ps = Native.POINTER_SIZE;
        byte[] pat = new byte[ps];
        for (int i = 0; i < ps; i++) pat[i] = (byte) ((value >> (8 * i)) & 0xFF);

        for (int rgi = 0; rgi < regions.size(); rgi++) {
            if (budget <= 0) break;
            MemoryRegion r = regions.get(rgi);

            long remain = Math.min(r.size, budget);
            long off = 0;
            while (off < remain) {
                int readSize = (int) Math.min(REGION_READ_SLICE, remain - off);
                byte[] data = readBytes(h, r.base + off, readSize);
                if (data == null || data.length == 0) break;

                for (int i = 0; i + pat.length <= data.length; i += alignment) {
                    boolean match = true;
                    for (int j = 0; j < pat.length; j++) {
                        if (data[i + j] != pat[j]) { match = false; break; }
                    }
                    if (match) {
                        long hit = r.base + off + i;
                        out.add(hit);
                        if (out.size() >= maxResults) return out;
                    }
                }

                off += data.length;
                budget -= data.length;
                if (budget <= 0) break;
            }
        }
        return out;
    }
    // Legacy wrapper (kept for completeness; not used in the main flow)
    @SuppressWarnings("unused")
    private static List<Long> findPointerOccurrences(HANDLE h, long value, int alignment, int maxResults) {
        return findPointerOccurrences(h, iterateReadablePrivateRegions(h), value, alignment, maxResults);
    }

    // ---- Entry block recognition & reconstruction ----

    private static final class BlockSpan {
        final long firstEntryVA;
        final int  count;
        BlockSpan(long firstEntryVA, int count) { this.firstEntryVA = firstEntryVA; this.count = count; }
    }

    private static BlockSpan expandContiguousBlock(HANDLE h, long entryVA, int ptrOff, int stride,
                                                   long qCharDelta, int maxSteps) {
        // backward
        long first = entryVA;
        int back = 0;
        int badStreak = 0;

        while (back < maxSteps) {
            long prev = first - stride;
            if (!isReadable(h, prev + ptrOff, Native.POINTER_SIZE)) break;

            String s = readEntryLine(h, prev, ptrOff, qCharDelta);
            if (s == null) break;

            if (!isPrintableOrEmpty(s)) {
                badStreak++;
                if (badStreak >= 4) break;
            } else {
                badStreak = 0;
            }

            first = prev;
            back++;
        }

        // forward
        long cur = entryVA;
        int fwd = 1; // include current
        badStreak = 0;

        while (fwd < maxSteps) {
            long next = cur + stride;
            if (!isReadable(h, next + ptrOff, Native.POINTER_SIZE)) break;

            String s = readEntryLine(h, next, ptrOff, qCharDelta);
            if (s == null) break;

            if (!isPrintableOrEmpty(s)) {
                badStreak++;
                if (badStreak >= 4) break;
            } else {
                badStreak = 0;
            }

            cur = next;
            fwd++;
        }

        int total = back + fwd;
        if (total < 1) return null;
        return new BlockSpan(first, total);
    }

    private static boolean isPrintableOrEmpty(String s) {
        if (s == null) return false;
        if (s.length() == 0) return true; // allow empty console lines
        int ctrl = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x09 && (c != '\r' && c != '\n' && c != '\t')) ctrl++;
        }
        return ctrl < 4;
    }

    private static int sampleDecode(HANDLE h, long base, int ptrOff, int stride,
                                    long qCharDelta, int sample) {
        int ok = 0;
        for (int i = 0; i < sample; i++) {
            long e = base + (long) i * stride;
            String s = readEntryLine(h, e, ptrOff, qCharDelta);
            if (s != null) ok++;
        }
        return ok;
    }

    // Overload that reuses pre-enumerated regions
    private static String tryReconstructRingExactly(HANDLE h, List<MemoryRegion> regions, EntryArrayCandidate cand) {
        List<Long> owners = findPointerOccurrences(h, regions, cand.baseVA, Native.POINTER_SIZE, 512);
        if (owners.isEmpty()) {
            log("    No owners of array base pointer (0x%016X).", Long.valueOf(cand.baseVA));
            return null;
        }

        final int WINDOW = 512;

        for (int oi = 0; oi < owners.size(); oi++) {
            long site = owners.get(oi);
            long start = Math.max(0, site - WINDOW);
            byte[] buf = readBytes(h, start, WINDOW * 2);
            if (buf == null || buf.length < 12) continue;

            for (int off = 0; off + 12 <= buf.length; off += 4) {
                int max   = toIntLE(buf, off);
                int first = toIntLE(buf, off + 4);
                int num   = toIntLE(buf, off + 8);

                if (max <= 0 || max > 2_000_000) continue;
                if (first < 0 || first >= max) continue;
                if (num <= 0 || num > max) continue; // ignore empty rings

                int sample = num < 32 ? num : 32;
                if (sample <= 0) continue;

                int ok = 0;
                for (int i = 0; i < sample; i++) {
                    int real = (first + i) % max;
                    long entryVA = cand.baseVA + (long) real * cand.stride;
                    String s = readEntryLine(h, entryVA, cand.ptrOffset, cand.qCharDelta);
                    if (s != null && s.length() != 0) ok++;
                }
                if (ok == 0) continue;

                log("    Ring indices found: first=%d num=%d max=%d",
                        Integer.valueOf(first), Integer.valueOf(num), Integer.valueOf(max));

                return reconstructByRing(h, cand.baseVA, cand.stride, cand.ptrOffset,
                        cand.qCharDelta, first, num, max);
            }
        }
        return null;
    }
    // Legacy wrapper (unused in main flow)
    @SuppressWarnings("unused")
    private static String tryReconstructRingExactly(HANDLE h, EntryArrayCandidate cand) {
        return tryReconstructRingExactly(h, iterateReadablePrivateRegions(h), cand);
    }

    private static String reconstructByRing(HANDLE h, long base, int stride, int ptrOff,
                                            long qCharDelta, int first, int num, int max) {
        long bytes = (long) max * stride;
        if (bytes <= 0 || !isReadable(h, base, (int) Math.min(bytes, 4096))) return null;

        int est = num * 128;
        if (est < 0) est = 0;
        if (est > 8_000_000) est = 8_000_000;
        StringBuilder out = new StringBuilder(est);

        for (int i = 0; i < num; i++) {
            int real = (first + i) % max;
            long entryVA = base + (long) real * stride;
            String line = readEntryLine(h, entryVA, ptrOff, qCharDelta);
            if (line == null) line = "";
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /** RAM-only linear reconstruction (contiguous block), tolerant to empty lines. */
    private static String reconstructLinearBlock(HANDLE h, EntryArrayCandidate cand) {
        long bytes = (long) cand.span * cand.stride;
        if (bytes <= 0 || !isReadable(h, cand.baseVA, (int) Math.min(bytes, 4096))) return null;

        int est = cand.span * 128;
        if (est < 0) est = 0;
        if (est > 8_000_000) est = 8_000_000;
        StringBuilder out = new StringBuilder(est);

        for (int i = 0; i < cand.span; i++) {
            long entryVA = cand.baseVA + (long) i * cand.stride;
            String line = readEntryLine(h, entryVA, cand.ptrOffset, cand.qCharDelta);
            if (line == null) line = "";
            out.append(line).append('\n');
        }
        String result = out.toString();
        if (result.length() == 0) return null;
        return result;
    }

    // ---- Smart decoding ----

    // Score "plausibility": length + share of printable chars, small bonuses for typical punctuation.
    private static int scoreDecoded(String s) {
        if (s == null) return -1;
        final int len = s.length();
        if (len == 0) return 0;

        int printable = 0, ctrl = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == 0) { ctrl++; continue; }
            if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') printable++; else ctrl++;
        }
        int score = len;
        if (printable < (len * 3) / 4) score -= (len / 2);
        if (ctrl > 2) score -= 50;
        if (s.indexOf(':') >= 0) score += 3;
        if (s.indexOf('/') >= 0 || s.indexOf('\\') >= 0) score += 3;
        if (s.indexOf('.') >= 0) score += 2;
        return score;
    }

    // Try both interpretations of pointer p (QString::d + Δ, or raw char16_t*), pick the better one.
    private static String decodePreferQStringOrChar(HANDLE h, long p, long qCharDelta) {
        if (p == 0) return null;

        // allow very long lines (e.g., Libraries list paths)
        String sQString = readUtf16StringUnsafe(h, p + qCharDelta, 8192);
        String sChars   = readUtf16StringUnsafe(h, p,              8192);

        int a = scoreDecoded(sQString);
        int b = scoreDecoded(sChars);

        if (b > a + 2) return sChars; // prefer raw char* if clearly better
        return sQString;               // slightly biased toward QString interpretation
    }

    private static String readEntryLine(HANDLE h, long entryVA, int ptrOff, long qCharDelta) {
        if (!isReadable(h, entryVA + ptrOff, Native.POINTER_SIZE)) return null;
        long p = readPtr(h, entryVA + ptrOff);
        if (p == 0) return null;
        return decodePreferQStringOrChar(h, p, qCharDelta);
    }

    // ---- Memory enumeration & safe reads ----

    private static List<MemoryRegion> iterateReadablePrivateRegions(HANDLE h) {
        List<MemoryRegion> out = new ArrayList<MemoryRegion>();
        Pointer addr = Pointer.createConstant(0);
        MEMORY_BASIC_INFORMATION mbi = new MEMORY_BASIC_INFORMATION();

        while (true) {
            SIZE_T got = K32.INSTANCE.VirtualQueryEx(h, addr, mbi, new SIZE_T(mbi.size()));
            if (got == null || got.longValue() == 0) break;

            long base = Pointer.nativeValue(mbi.BaseAddress);
            long size = mbi.RegionSize.longValue();

            boolean committed = (mbi.State.intValue() & MEM_COMMIT) != 0;
            boolean guarded   = (mbi.Protect.intValue() & PAGE_GUARD) != 0;
            boolean noaccess  = (mbi.Protect.intValue() & PAGE_NOACCESS) != 0;
            boolean readable  = READABLE_PROT.contains(mbi.Protect.intValue());
            boolean isPrivate = (mbi.Type != null && mbi.Type.intValue() == MEM_PRIVATE);

            if (committed && readable && !guarded && !noaccess && isPrivate && size > 0) {
                out.add(new MemoryRegion(base, size));
            }

            long next = base + size;
            if (next <= base) break;
            addr = Pointer.createConstant(next);
        }
        return out;
    }

    private static boolean isReadable(HANDLE h, long addr, int len) {
        MEMORY_BASIC_INFORMATION mbi = new MEMORY_BASIC_INFORMATION();
        SIZE_T got = K32.INSTANCE.VirtualQueryEx(h, Pointer.createConstant(addr), mbi, new SIZE_T(mbi.size()));
        if (got == null || got.longValue() == 0) return false;

        boolean committed = (mbi.State.intValue() & MEM_COMMIT) != 0;
        boolean guarded   = (mbi.Protect.intValue() & PAGE_GUARD) != 0;
        boolean noaccess  = (mbi.Protect.intValue() & PAGE_NOACCESS) != 0;
        boolean readable  = READABLE_PROT.contains(mbi.Protect.intValue());

        long regionStart = Pointer.nativeValue(mbi.BaseAddress);
        long regionEnd   = regionStart + mbi.RegionSize.longValue();
        return committed && readable && !guarded && !noaccess && addr >= regionStart && (addr + len) <= regionEnd;
    }

    private static byte[] readBytes(HANDLE h, long addr, int len) {
        if (len <= 0) return new byte[0];
        if (!isReadable(h, addr, len)) return null;

        Memory buf = new Memory(len);
        IntByReference br = new IntByReference(0);
        boolean ok = K32.INSTANCE.ReadProcessMemory(h, Pointer.createConstant(addr), buf, len, br);
        if (!ok) return null;

        int got = br.getValue();
        if (got <= 0) return null;
        return buf.getByteArray(0, got);
    }

    private static int toIntLE(byte[] b, int off) {
        if (off + 4 > b.length) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long readPtr(HANDLE h, long addr) {
        int sz = Native.POINTER_SIZE;
        byte[] b = readBytes(h, addr, sz);
        if (b == null || b.length < sz) return 0L;
        long v = 0L;
        for (int i = 0; i < sz; i++) v |= ((long) (b[i] & 0xFF)) << (8 * i);
        return v;
    }

    private static String readUtf16StringUnsafe(HANDLE h, long charDataVA, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int step = 512;
        int read = 0;
        outer:
        while (read < maxChars * 2) {
            int toRead = Math.min(step, maxChars * 2 - read);
            byte[] chunk = readBytes(h, charDataVA + read, toRead);
            if (chunk == null || chunk.length == 0) break;

            for (int i = 0; i + 1 < chunk.length; i += 2) {
                int cu = (chunk[i] & 0xFF) | ((chunk[i + 1] & 0xFF) << 8);
                if (cu == 0) break outer;
                sb.append((char) cu);
            }
            read += chunk.length;
        }
        return sb.toString();
    }

    // ---- helper types ----

    private static final class MemoryRegion {
        final long base, size;
        MemoryRegion(long base, long size) { this.base = base; this.size = size; }
    }

    private static final class AnchorHit {
        final String anchorText;
        final long charDataVA; // address of the first UTF‑16 code unit (QString::data)
        AnchorHit(String anchorText, long charDataVA) { this.anchorText = anchorText; this.charDataVA = charDataVA; }
    }

    private static final class QStringDescriptor {
        final long dPointer, charDataVA, length;
        QStringDescriptor(long dPointer, long charDataVA, long length) {
            this.dPointer = dPointer; this.charDataVA = charDataVA; this.length = length;
        }
    }

    private static final class EntryArrayCandidate {
        final long baseVA;     // first entry address in the contiguous block
        final int  stride;     // bytes per entry
        final int  ptrOffset;  // offset of pointer within entry
        final int  span;       // number of entries in the block
        final long qCharDelta; // charDataVA - dPointer
        EntryArrayCandidate(long baseVA, int stride, int ptrOffset, int span, long qCharDelta) {
            this.baseVA = baseVA; this.stride = stride; this.ptrOffset = ptrOffset;
            this.span = span; this.qCharDelta = qCharDelta;
        }
    }

    // ---- minimal Win32 types and bindings (JNA core only) ----

    public static class HANDLE extends PointerType {}

    private static final HANDLE INVALID_HANDLE_VALUE = new HANDLE() {{
        setPointer(Pointer.createConstant(Native.POINTER_SIZE == 8 ? -1L : 0xFFFFFFFFL));
    }};

    public static class DWORD extends IntegerType {
        public DWORD() { this(0); }
        public DWORD(long value) { super(4, value, true); }
    }

    public static class SIZE_T extends IntegerType {
        public SIZE_T() { this(0); }
        public SIZE_T(long value) { super(Native.POINTER_SIZE, value, true); }
    }

    public static class PROCESSENTRY32W extends Structure {
        public DWORD   dwSize       = new DWORD();
        public DWORD   cntUsage     = new DWORD();
        public DWORD   th32ProcessID= new DWORD();
        public Pointer th32DefaultHeapID; // ULONG_PTR
        public DWORD   th32ModuleID = new DWORD();
        public DWORD   cntThreads   = new DWORD();
        public DWORD   th32ParentProcessID = new DWORD();
        public int     pcPriClassBase; // LONG
        public DWORD   dwFlags      = new DWORD();
        public char[]  szExeFile    = new char[MAX_PATH];

        @Override protected List<String> getFieldOrder() {
            List<String> f = new ArrayList<String>(10);
            f.add("dwSize"); f.add("cntUsage"); f.add("th32ProcessID"); f.add("th32DefaultHeapID");
            f.add("th32ModuleID"); f.add("cntThreads"); f.add("th32ParentProcessID"); f.add("pcPriClassBase");
            f.add("dwFlags"); f.add("szExeFile");
            return f;
        }
    }

    public static class MEMORY_BASIC_INFORMATION extends Structure {
        public Pointer BaseAddress;
        public Pointer AllocationBase;
        public DWORD   AllocationProtect;
        public SIZE_T  RegionSize;
        public DWORD   State;
        public DWORD   Protect;
        public DWORD   Type;

        @Override protected List<String> getFieldOrder() {
            List<String> f = new ArrayList<String>(7);
            f.add("BaseAddress"); f.add("AllocationBase"); f.add("AllocationProtect");
            f.add("RegionSize");  f.add("State");          f.add("Protect"); f.add("Type");
            return f;
        }
    }

    private interface K32 extends StdCallLibrary {
        K32 INSTANCE = Native.loadLibrary("kernel32", K32.class);

        HANDLE  OpenProcess(int dwDesiredAccess, boolean bInheritHandle, int dwProcessId);
        boolean CloseHandle(HANDLE hObject);

        HANDLE  CreateToolhelp32Snapshot(int dwFlags, int th32ProcessID);
        boolean Process32FirstW(HANDLE hSnapshot, PROCESSENTRY32W lppe);
        boolean Process32NextW (HANDLE hSnapshot, PROCESSENTRY32W lppe);

        boolean IsWow64Process(HANDLE hProcess, IntByReference wow64Process);

        SIZE_T  VirtualQueryEx(HANDLE hProcess, Pointer lpAddress, MEMORY_BASIC_INFORMATION lpBuffer, SIZE_T dwLength);
        boolean ReadProcessMemory(HANDLE hProcess, Pointer lpBaseAddress, Pointer lpBuffer, int nSize, IntByReference lpNumberOfBytesRead);
    }
}
