package dev.kostromdan.mods.crash_assistant.common.utils;


import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;

public interface ManualCrashThrower {
    static void crashGame(String msg) {
        CrashReport crashreport = new CrashReport(msg, new Throwable(msg));
        throw new Error(new ReportedException(crashreport));
    }
}
