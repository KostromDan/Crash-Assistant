package dev.kostromdan.mods.crash_assistant.utils;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;

public class HeapDumper {
    public static String createHeapDump() throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
        String heapDumpFilePath = "heapdump_" + timestamp + ".hprof";
        com.sun.management.HotSpotDiagnosticMXBean mxBean = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
        mxBean.dumpHeap(heapDumpFilePath, false);
        return heapDumpFilePath;
    }
}
