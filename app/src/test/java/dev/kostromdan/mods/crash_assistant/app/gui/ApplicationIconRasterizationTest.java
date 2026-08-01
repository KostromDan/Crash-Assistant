package dev.kostromdan.mods.crash_assistant.app.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;

public final class ApplicationIconRasterizationTest {
    private static final int[] SIZES = {16, 20, 22, 24, 32, 40, 48, 64, 96, 128, 256, 512};

    private ApplicationIconRasterizationTest() {
    }

    public static void main(String[] args) throws Exception {
        assertRasterizes(GeneratedLogoIcon.ICON, "logo", SIZES);
        assertRasterizes(GeneratedInternetIcon.ICON, "internet",
                new int[]{12, 16, 20, 24, 32, 48});
        assertSvgSourcesAreExcludedFromRuntime();
        assertButtonIconTracksActualSize();
        assertLinuxDesktopEntry();
        System.out.println("Application SVG rasterization tests passed.");
    }

    private static void assertRasterizes(VectorIcon icon, String iconName, int[] sizes) {
        for (int size : sizes) {
            Image rendered = icon.createMultiResolutionImage(size);
            assertEquals(size, rendered.getWidth(null), "width");
            assertEquals(size, rendered.getHeight(null), "height");

            BufferedImage raster = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = raster.createGraphics();
            try {
                graphics.drawImage(rendered, 0, 0, null);
            } finally {
                graphics.dispose();
            }

            int opaquePixels = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if ((raster.getRGB(x, y) >>> 24) != 0) opaquePixels++;
                }
            }
            if (opaquePixels == 0) {
                throw new AssertionError(iconName + " rendered at " + size + "px is fully transparent");
            }
        }
    }

    private static void assertSvgSourcesAreExcludedFromRuntime() {
        ClassLoader classLoader = ApplicationIconRasterizationTest.class.getClassLoader();
        if (classLoader.getResource("assets/logo.svg") != null
                || classLoader.getResource("assets/internet.svg") != null) {
            throw new AssertionError("SVG source files must not be included in runtime resources");
        }
    }

    private static void assertButtonIconTracksActualSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = new JButton();
            button.setMargin(new Insets(0, 0, 0, 0));
            button.setBorder(BorderFactory.createEmptyBorder(2, 3, 4, 5));
            button.setSize(40, 30);
            SvgButtonIcon.install(button, GeneratedInternetIcon.ICON);
            assertEquals(24, button.getIcon().getIconWidth(), "initial button icon width");

            button.setSize(50, 42);
            ComponentEvent resized = new ComponentEvent(button, ComponentEvent.COMPONENT_RESIZED);
            for (ComponentListener listener : button.getComponentListeners()) {
                listener.componentResized(resized);
            }
            assertEquals(36, button.getIcon().getIconWidth(), "resized button icon width");
        });
    }

    private static void assertLinuxDesktopEntry() {
        String entry = LinuxDesktopIntegration.createDesktopEntry(
                Paths.get("/tmp/crash assistant/logo.png")
        );
        assertContains(entry, "Icon=/tmp/crash assistant/logo.png\n");
        assertContains(entry, "NoDisplay=true\n");
        assertContains(entry,
                "StartupWMClass=dev-kostromdan-mods-crash_assistant-app-class_loading-Boot\n");
        String standaloneEntry = LinuxDesktopIntegration.createDesktopEntry(
                Paths.get("/tmp/crash assistant/logo.png"),
                "dev-kostromdan-mods-crash_assistant-app-CrashAssistantApp"
        );
        assertContains(standaloneEntry,
                "StartupWMClass=dev-kostromdan-mods-crash_assistant-app-CrashAssistantApp\n");
    }

    private static void assertContains(String value, String expectedPart) {
        if (!value.contains(expectedPart)) {
            throw new AssertionError("Missing desktop entry value: " + expectedPart);
        }
    }

    private static void assertEquals(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError("Unexpected " + description + ": expected " + expected + ", got " + actual);
        }
    }
}
