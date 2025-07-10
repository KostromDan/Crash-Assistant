package net.minecraftforge.fml.crash_assistant;

// Idea taken from https://github.com/CleanroomMC/CleanroomRelauncher
public class ExitVMBypass {

    public static void exit(int status) {
        exit$(status);
    }

    private static void exit$(int status) {
        exit$$(status);
    }

    private static void exit$$(int status) {
        System.exit(status);
    }

}
