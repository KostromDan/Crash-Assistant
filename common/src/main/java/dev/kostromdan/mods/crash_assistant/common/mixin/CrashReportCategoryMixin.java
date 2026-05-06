package dev.kostromdan.mods.crash_assistant.common.mixin;

import net.minecraft.CrashReportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

@Mixin(CrashReportCategory.class)
public abstract class CrashReportCategoryMixin {
    /**
     * @author KostromDan
     * @reason Fixes vanilla crash MC-307905 during crash report generation when StackTraceElement#getFileName() returns null.
     * Without this fix, crash report generation itself crashes, preventing the original crash report from being created.
     */
    @Redirect(
            method = "validateStackTrace",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z",
                    ordinal = 1
            )
    )
    private boolean crashAssistant$nullSafeStackTraceFileNameEquals(final String self, final Object other) {
        return Objects.equals(self, other);
    }
}
