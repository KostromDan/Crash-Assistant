package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class TerminatedProcessesFinder {

    // Windows Event Log (wevtapi) interface via JNA
    interface Wevtapi extends Library {
        int EvtQueryChannelPath = 0x00000001;
        int EvtRenderEventXml = 1; // EVT_RENDER_FLAGS
        int EvtFormatMessageEvent = 1;

        HANDLE EvtQuery(HANDLE session, WString path, WString query, int flags);

        boolean EvtNext(HANDLE resultSet, int eventArraySize, HANDLE[] events, int timeout, int flags, IntByReference returned);

        boolean EvtRender(HANDLE context, HANDLE event, int flags, int bufferSize, Pointer buffer, IntByReference bufferUsed, IntByReference propertyCount);

        HANDLE EvtOpenPublisherMetadata(HANDLE session, WString publisherId, WString logFilePath, int locale, int flags);

        boolean EvtFormatMessage(HANDLE publisherMetadata, HANDLE event, int messageId, int valueCount, Pointer values, int flags, int bufferSize, char[] buffer, IntByReference bufferUsed);

        boolean EvtClose(HANDLE handle);
    }

    // Lazy, compatible loader: works with JNA 4.x (loadLibrary) and 5.x (load)
    private static volatile Wevtapi WEVT;

    private static Wevtapi wevt() {
        if (WEVT == null) {
            synchronized (TerminatedProcessesFinder.class) {
                if (WEVT == null) {
                    try {
                        // Avoid class-init errors on non-Windows by checking OS first.
                        if (!PlatformHelp.isWindows()) {
                            return null;
                        }
                        try {
                            Method m = Native.class.getMethod("load", String.class, Class.class);
                            WEVT = (Wevtapi) m.invoke(null, "wevtapi", Wevtapi.class);
                        } catch (NoSuchMethodException ignored) {
                            WEVT = (Wevtapi) Native.loadLibrary("wevtapi", Wevtapi.class);
                        }
                    } catch (Throwable t) {
                        CrashAssistantApp.LOGGER.error("Failed to load wevtapi via JNA: ", t);
                        throw new RuntimeException(t);
                    }
                }
            }
        }
        return WEVT;
    }

    private static final DateTimeFormatter HUMAN_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z");
    private static final String SEPARATOR = "-----------------------";

    // FILETIME constants and helpers
    private static final BigInteger FILETIME_EPOCH_DELTA_100NS = BigInteger.valueOf(116444736000000000L);
    private static final BigInteger HNS_PER_MS = BigInteger.valueOf(10_000L);

    // Patterns to enrich details from provider messages
    private static final Pattern RX_FAULT_PID = Pattern.compile("(?i)\\bfaulting\\s+process\\s+id\\s*:\\s*(0x[0-9a-fA-F]+|\\d+)");
    private static final Pattern RX_FAULT_STARTTIME = Pattern.compile("(?i)\\bfaulting\\s+application\\s+start\\s+time\\s*:\\s*(0x[0-9a-fA-F]+|\\d+)");
    private static final Pattern RX_EXCEPTION_CODE = Pattern.compile("(?i)\\bexception\\s+code\\s*:\\s*(0x[0-9a-fA-F]+|\\d+)");
    private static final Pattern RX_NVIDIA_PID_TID = Pattern.compile("\\(pid=(\\d+)\\s+tid=(\\d+)\\s+([^\\)]+)\\)");

    /**
     * Collects recent Windows Application log entries that may indicate a process crash/termination.
     * <p>
     * Scope:
     * - Log: Application
     * - Levels: Error and Critical
     * - Time window: last ~15 seconds
     * <p>
     * The method writes a plain‑text report to the current working directory
     * only if matching entries were found. The filename is returned regardless.
     * <p>
     * On non‑Windows systems, this method does nothing and returns the filename.
     */
    public static String getTerminatedByWinProcessLogs() {
        String fileName = "win_event" + System.currentTimeMillis() + ".txt";
        Path targetPath = Paths.get(fileName);

        try {
            if (!PlatformHelp.isWindows()) {
                return fileName; // do nothing on non-Windows
            }

            // Query recent entries
            List<String> blocks = queryRecentAppLog(15_000); // 15 seconds

            if (blocks.isEmpty()) {
                return fileName; // no file created if nothing to report
            }

            String header = ""
                    + "Detected that Windows reported recent critical or error events in the Application log,\n"
                    + "which can indicate that a process (including the Minecraft JVM) terminated unexpectedly.\n"
                    + "Scope: Application log; Levels: Error & Critical; Window: last ~15 seconds.\n"
                    + "\n"
                    + "If no java.exe (or related JVM processes) are listed below, you can disregard this message.\n"
                    + "To inspect these events manually:\n"
                    + "  1) Press Win+R, type \"eventvwr.msc\", press Enter.\n"
                    + "  2) Open \"Windows Logs\" → \"Application\".\n"
                    + "  3) Sort by Date and look for entries with Level = Error or Critical near the crash time.\n";

            StringBuilder out = new StringBuilder();
            out.append(header).append("\n");
            for (String block : blocks) {
                out.append(SEPARATOR).append("\n")
                        .append(block).append("\n")
                        .append(SEPARATOR).append("\n\n");
            }

            Files.write(targetPath, out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            // Never propagate to the caller; keep the app resilient.
            try {
                CrashAssistantApp.LOGGER.error("TerminatedProcessesFinder: Windows Event Log query failed.", t);
            } catch (Throwable ignored) {
                // Swallow logging issues as well
            }
        }

        return fileName;
    }

    // ---------------------------- Query & render ----------------------------

    private static List<String> queryRecentAppLog(long maxAgeMillis) throws Exception {
        List<String> results = new ArrayList<>();

        // Level = 1 (Critical) or 2 (Error), within the last maxAgeMillis
        String xpath = "*[System[(Level=1 or Level=2) and TimeCreated[timediff(@SystemTime) <= " + maxAgeMillis + "]]]";

        Wevtapi api = wevt();
        if (api == null) return results;

        HANDLE query = api.EvtQuery(null, new WString("Application"), new WString(xpath), Wevtapi.EvtQueryChannelPath);
        if (query == null) {
            throw new IOException("EvtQuery returned null for Application log.");
        }

        try {
            HANDLE[] events = new HANDLE[32];
            IntByReference returned = new IntByReference(0);

            while (api.EvtNext(query, events.length, events, 0, 0, returned)) {
                int count = returned.getValue();
                for (int i = 0; i < count; i++) {
                    HANDLE evt = events[i];
                    try {
                        String xml = renderEventXml(api, evt);
                        EventInfo info = parseEventXml(xml);
                        String message = formatMessage(api, evt, info.providerName);
                        String block = renderBlock(info, message);
                        results.add(block);
                    } finally {
                        safeClose(api, events[i]);
                        events[i] = null;
                    }
                }
                returned.setValue(0);
            }
        } finally {
            safeClose(api, query);
        }

        return results;
    }

    private static String renderEventXml(Wevtapi api, HANDLE evt) throws IOException {
        IntByReference used = new IntByReference(0);
        IntByReference count = new IntByReference(0);

        api.EvtRender(null, evt, Wevtapi.EvtRenderEventXml, 0, Pointer.NULL, used, count);
        int needed = used.getValue();
        if (needed <= 0) {
            throw new IOException("EvtRender size was non-positive.");
        }

        Memory buf = new Memory(needed);
        if (!api.EvtRender(null, evt, Wevtapi.EvtRenderEventXml, (int) buf.size(), buf, used, count)) {
            throw new IOException("EvtRender failed.");
        }
        return buf.getWideString(0);
    }

    private static String formatMessage(Wevtapi api, HANDLE evt, String providerName) {
        if (providerName == null || providerName.isEmpty()) return "";

        HANDLE meta = api.EvtOpenPublisherMetadata(null, new WString(providerName), null, 0, 0);
        if (meta == null) return "";

        try {
            IntByReference used = new IntByReference(0);
            boolean ok = api.EvtFormatMessage(meta, evt, 0, 0, null, Wevtapi.EvtFormatMessageEvent, 0, null, used);
            int needed = used.getValue();
            if (!ok && needed > 0) {
                char[] buf = new char[needed];
                ok = api.EvtFormatMessage(meta, evt, 0, 0, null, Wevtapi.EvtFormatMessageEvent, needed, buf, used);
                if (ok) {
                    return new String(buf, 0, used.getValue()).trim();
                }
            }
            return "";
        } catch (Throwable ignored) {
            return "";
        } finally {
            safeClose(api, meta);
        }
    }

    private static void safeClose(Wevtapi api, HANDLE h) {
        try {
            if (h != null) api.EvtClose(h);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------- Parse & normalize ----------------------------

    private static class KV {
        String key;
        String value;

        KV(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class EventInfo {
        String providerName;
        String level;     // Human readable name
        String eventId;
        String timeIso;   // SystemTime from XML
        List<KV> pairs = new ArrayList<>();
    }

    private static EventInfo parseEventXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xml)));

        EventInfo info = new EventInfo();
        Element root = doc.getDocumentElement();

        NodeList systemList = root.getElementsByTagName("System");
        if (systemList.getLength() > 0) {
            Element sys = (Element) systemList.item(0);
            Node provider = sys.getElementsByTagName("Provider").item(0);
            if (provider instanceof Element) {
                info.providerName = ((Element) provider).getAttribute("Name");
            }
            Node eventId = sys.getElementsByTagName("EventID").item(0);
            if (eventId != null) info.eventId = eventId.getTextContent();

            Node timeCreated = sys.getElementsByTagName("TimeCreated").item(0);
            if (timeCreated instanceof Element) {
                info.timeIso = ((Element) timeCreated).getAttribute("SystemTime");
            }

            Node levelNode = sys.getElementsByTagName("Level").item(0);
            if (levelNode != null) info.level = levelCodeToName(levelNode.getTextContent());
        }

        // Collect EventData and UserData
        int unnamed = 0;
        NodeList dataBlocks = root.getElementsByTagName("EventData");
        if (dataBlocks.getLength() == 0) dataBlocks = root.getElementsByTagName("UserData");

        for (int i = 0; i < dataBlocks.getLength(); i++) {
            Node n = dataBlocks.item(i);
            NodeList children = n.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node c = children.item(j);
                if (c instanceof Element) {
                    Element e = (Element) c;
                    if ("Data".equals(e.getNodeName())) {
                        String name = e.getAttribute("Name");
                        if (name == null || name.trim().isEmpty()) {
                            name = "Data[" + (++unnamed) + "]";
                        }
                        String val = e.getTextContent();
                        if (val != null && !val.trim().isEmpty()) {
                            info.pairs.add(new KV(name, val.trim()));
                        }
                    } else {
                        String val = e.getTextContent();
                        if (val != null && !val.trim().isEmpty()) {
                            info.pairs.add(new KV(e.getTagName(), val.trim()));
                        }
                    }
                }
            }
        }

        return info;
    }

    private static String levelCodeToName(String codeStr) {
        try {
            int code = Integer.parseInt(codeStr.trim());
            switch (code) {
                case 1:
                    return "Critical";
                case 2:
                    return "Error";
                case 3:
                    return "Warning";
                case 4:
                    return "Information";
                case 5:
                    return "Verbose";
                default:
                    return "Level " + code;
            }
        } catch (Exception e) {
            return codeStr;
        }
    }

    private static String renderBlock(EventInfo info, String message) {
        StringBuilder b = new StringBuilder();

        // Event time: decimal epoch millis first, then [human], then [original ISO].
        if (info.timeIso != null && !info.timeIso.isEmpty()) {
            try {
                Instant instant = Instant.parse(info.timeIso);
                ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
                b.append("Time: ").append(HUMAN_TIME_FMT.format(zdt));
            } catch (Exception e) {
                b.append("Time: ").append(info.timeIso);
            }
            b.append("\n");
        }

        // Provider / ID / Level
        if (info.providerName != null) b.append("Source: ").append(info.providerName).append("\n");
        if (info.eventId != null) b.append("Event ID: ").append(info.eventId).append("\n");
        if (info.level != null) b.append("Level: ").append(info.level).append("\n");

        if (message != null && !message.isEmpty()) {
            b.append("\nMessage:\n");
            b.append(indent(cleanMultiline(message))).append("\n");
        }

        // Details: normalize PID, start time (FILETIME), exception code if we can find them.
        List<String> detail = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (KV kv : info.pairs) {
            String k = kv.key;
            String v = kv.value;
            String kl = k.toLowerCase(Locale.ROOT);
            seen.add(kl);

            if (kl.contains("process id") || kl.equals("pid") || kl.equals("processid")) {
                String norm = normalizePid(v);
                if (norm != null) {
                    detail.add("  - Process ID: " + norm + " [" + v + "]");
                } else {
                    detail.add("  - " + k + ": " + v);
                }
            } else if (kl.contains("start time") || kl.contains("starttime")) {
                DecTime dt = normalizeFiletime(v);
                if (dt != null) {
                    detail.add("  - Start Time: " + (dt.human != null ? dt.human : "") + " [" + dt.raw + "]");
                } else {
                    detail.add("  - " + k + ": " + v);
                }
            } else if (kl.contains("exception code")) {
                DecHex dh = normalizeHexOrDec(v);
                if (dh != null) {
                    detail.add("  - Exception Code: " + dh.dec + " [" + dh.hex + "]");
                } else {
                    detail.add("  - " + k + ": " + v);
                }
            } else {
                detail.add("  - " + k + ": " + v);
            }
        }

        // Derive additional info from the free-form message
        Derived d = deriveFromMessage(message);
        if (d != null) {
            if (d.pidDec != null) {
                detail.add("  - Process ID: " + d.pidDec + (d.pidHex != null ? " [" + d.pidHex + "]" : ""));
            }
            if (d.tidDec != null) {
                detail.add("  - Thread ID: " + d.tidDec);
            }
            if (d.startTime != null) {
                detail.add("  - Start Time: " + (d.startTime.human != null ? d.startTime.human : "") + " [" + d.startTime.raw + "]");
            }
            if (d.exception != null) {
                detail.add("  - Exception Code: " + d.exception.dec + " [" + d.exception.hex + "]");
            }
        }

        if (!detail.isEmpty()) {
            b.append("\nDetails:\n");
            for (String line : detail) b.append(line).append("\n");
        }

        return b.toString().trim();
    }

    private static class DecHex {
        String dec;
        String hex;
    }

    private static class DecTime {
        String decTicks;
        String human;
        String raw;
    }

    private static class Derived {
        Long pidDec;
        String pidHex;
        Long tidDec;
        DecTime startTime;
        DecHex exception;
    }

    private static Derived deriveFromMessage(String msg) {
        if (msg == null || msg.isEmpty()) return null;
        Derived d = new Derived();

        Matcher mPid = RX_FAULT_PID.matcher(msg);
        if (mPid.find()) {
            String raw = mPid.group(1);
            Long dec = parseMaybeHexToLong(raw);
            if (dec != null) {
                d.pidDec = dec;
                if (raw.startsWith("0x") || raw.startsWith("0X")) d.pidHex = raw;
            }
        }
        Matcher mTime = RX_FAULT_STARTTIME.matcher(msg);
        if (mTime.find()) {
            String raw = mTime.group(1);
            DecTime dt = normalizeFiletime(raw);
            if (dt != null) d.startTime = dt;
        }
        Matcher mExc = RX_EXCEPTION_CODE.matcher(msg);
        if (mExc.find()) {
            String raw = mExc.group(1);
            DecHex dh = normalizeHexOrDec(raw);
            if (dh != null) d.exception = dh;
        }
        Matcher mNv = RX_NVIDIA_PID_TID.matcher(msg);
        if (mNv.find()) {
            try {
                d.pidDec = Long.parseLong(mNv.group(1));
            } catch (Exception ignored) {
            }
            try {
                d.tidDec = Long.parseLong(mNv.group(2));
            } catch (Exception ignored) {
            }
            // mNv.group(3) contains process name and arch; kept inside the Message section.
        }

        return d;
    }

    private static String normalizePid(String raw) {
        Long dec = parseMaybeHexToLong(raw);
        return dec == null ? null : dec.toString();
    }

    private static DecHex normalizeHexOrDec(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String t = raw.trim();
        DecHex dh = new DecHex();
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) {
                long val = Long.parseUnsignedLong(t.substring(2), 16);
                dh.dec = Long.toUnsignedString(val);
                dh.hex = t;
            } else {
                long val = Long.parseLong(t);
                dh.dec = Long.toString(val);
                dh.hex = "0x" + Long.toHexString(val);
            }
            return dh;
        } catch (Exception e) {
            return null;
        }
    }

    private static DecTime normalizeFiletime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String t = raw.trim();
        BigInteger ticks;
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) {
                ticks = new BigInteger(t.substring(2), 16);
            } else {
                ticks = new BigInteger(t);
            }
        } catch (Exception e) {
            return null;
        }
        BigInteger sinceUnix100ns = ticks.subtract(FILETIME_EPOCH_DELTA_100NS);
        BigInteger millisBI = sinceUnix100ns.divide(HNS_PER_MS);
        long millis;
        try {
            millis = millisBI.longValue();
        } catch (Exception e) {
            return null;
        }
        String human = HUMAN_TIME_FMT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
        DecTime dt = new DecTime();
        dt.decTicks = ticks.toString();
        dt.human = human;
        dt.raw = t;
        return dt;
    }

    private static String cleanMultiline(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static String indent(String s) {
        String[] lines = s.split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append("  ").append(line).append("\n");
        }
        return out.toString();
    }

    private static Long parseMaybeHexToLong(String s) {
        try {
            String t = s.trim();
            if (t.startsWith("0x") || t.startsWith("0X")) {
                return Long.parseUnsignedLong(t.substring(2), 16);
            }
            return Long.parseLong(t);
        } catch (Exception e) {
            return null;
        }
    }
}
