package dev.kostromdan.mods.crash_assistant.app.logs_analyser;

public class RendererParser {

    // Define constants to avoid creating new strings on every method call.
    private static final String GL_INFO_TAG = "GL info: ";
    private static final String RENDERER_TAG = "Renderer: '";
    private static final String GL_VERSION_TAG = " GL version";

    /**
     * Parses a log line to extract the renderer information in a highly efficient manner.
     *
     * @param logLine The log line to parse.
     * @return The renderer string if found, otherwise null.
     */
    public static String getRenderer(String logLine) {
        // Initial quick check to discard irrelevant lines immediately.
        if (logLine == null || !logLine.contains(GL_INFO_TAG)) {
            return null;
        }

        // --- Handle the format: "Renderer: 'AMD Radeon(TM) Graphics'" ---
        int rendererTagIndex = logLine.indexOf(RENDERER_TAG);
        if (rendererTagIndex != -1) {
            int startIndex = rendererTagIndex + RENDERER_TAG.length();
            int endIndex = logLine.indexOf('\'', startIndex);
            if (endIndex != -1) {
                return logLine.substring(startIndex, endIndex);
            }
        }

        // --- Handle the format: "GL info: AMD Radeon(TM) Graphics GL version" ---
        int glInfoIndex = logLine.indexOf(GL_INFO_TAG);
        if (glInfoIndex != -1) {
            int startIndex = glInfoIndex + GL_INFO_TAG.length();
            int endIndex = logLine.indexOf(GL_VERSION_TAG, startIndex);
            if (endIndex != -1) {
                return logLine.substring(startIndex, endIndex);
            }
        }

        return null; // Return null if "GL info:" is present but the format isn't recognized.
    }

    /**
     * Test function to validate the getRenderer method with sample log lines.
     */
    public static void main(String[] args) {
        String[] logSamples = {
                "[30Sep2025 17:38:33.221] [pool-2-thread-1/INFO] [EARLYDISPLAY/]: GL info: AMD Radeon(TM) Graphics GL version 4.6.0 Core Profile Context 25.9.1.250822, ATI Technologies Inc.",
                "\tGL info: ' Vendor: 'ATI Technologies Inc.' Version: '4.6.0 Compatibility Profile Context 25.9.1.250822' Renderer: 'AMD Radeon(TM) Graphics'",
                "  GL info: ' Vendor: 'ATI Technologies Inc.' Version: '4.6.0 Compatibility Profile Context 25.9.1.250822' Renderer: 'AMD Radeon(TM) Graphics'",
                "[02:35:21] [EarlyDisplay/INFO]: GL info: AMD Radeon(TM) Graphics GL version 4.6.0 Core Profile Context 25.9.1.250822, ATI Technologies Inc.",
                "[04:16:16] [fml-loadingscreen/INFO]: GL info: AMD Radeon(TM) Graphics GL version 3.2.0 Core Profile Context 25.9.1.250822, ATI Technologies Inc.",
                "[INFO] This is a completely unrelated log line.",
                "[WARN] This log contains GL info: but has a malformed structure."
        };

        System.out.println("--- Running Log Parser Test ---");
        for (int i = 0; i < logSamples.length; i++) {
            String line = logSamples[i];
            String result = getRenderer(line);
            System.out.println("\nTest Case " + (i + 1));
            System.out.println("Input:  " + line);
            System.out.println("Output: " + result);
        }
        System.out.println("\n--- Test Complete ---");
    }
}