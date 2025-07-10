package dev.kostromdan.mods.crash_assistant.common.utils;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;

public interface ManualCrashThrower {
    static void crashGame(String msg) {
        CrashReport crashreport = new CrashReport(msg, new Throwable(msg));
        CrashReportCategory crashreportcategory = crashreport.addCategory("Crash Assistant debug crash details");
        throw new Error(new ReportedException(crashreport));
    }
}
