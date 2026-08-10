package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;

public final class ModListTxtParserTest {
    private ModListTxtParserTest() {
    }

    public static void main(String[] args) throws Exception {
        testCurrentGeneratedTable();
        testTableBeforeNotesColumn();
        testDetailedLegacyFormat();
        testSimpleLegacyFormat();
        testRejectsCountMismatchAndUnrelatedText();
        System.out.println("modlist.txt parser tests passed.");
    }

    private static void testCurrentGeneratedTable() throws Exception {
        LinkedHashSet<Mod> source = new LinkedHashSet<Mod>();
        source.add(new Mod(
                "alpha.jar", "alpha", "Alpha", "1.2.3", true, false,
                new HashSet<String>(Arrays.asList("alpha.mixins.json")),
                new ArrayList<Mod>(), null, 123456L, "abc123"));
        source.add(new Mod("resource pack (resourcepack)"));

        Path file = Files.createTempFile("modlist-parser-current", ".txt");
        try {
            Mod.writeModlistTxt(file, source);
            LinkedHashSet<Mod> parsed = ModListTxtParser.parse(file);
            assertEquals(2, parsed.size(), "current format mod count");
            Mod alpha = parsed.iterator().next();
            assertEquals("alpha.jar", alpha.getJarName(), "current jar name");
            assertEquals("alpha", alpha.getModId(), "current mod id");
            assertEquals("Alpha", alpha.getName(), "current display name");
            assertEquals("1.2.3", alpha.getVersion(), "current version");
            assertEquals(Long.valueOf(123456L), alpha.getCurseForgeHash(), "current CurseForge hash");
            assertEquals("abc123", alpha.getModrinthHash(), "current Modrinth hash");
            assertTrue(alpha.getMixinConfigs().contains("alpha.mixins.json"), "current mixin config");
            assertEquals(Boolean.TRUE, alpha.IsMCreator(), "current notes column");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void testTableBeforeNotesColumn() throws Exception {
        String text =
                "Mods count: 2\n\n"
                        + "jar name  | isMCreator | isLoadedByConnector | mod id | mod name | mod version | mixin configs | modrinth hash | curseforge hash\n"
                        + "alpha.jar | MCreator mod | Connector mod | alpha | Alpha | 1.0 | alpha.mixins.json | deadbeef | 42\n"
                        + "    nested.jar | | | nested | Nested | 1.0 | | |\n"
                        + "beta.jar | | | beta | Beta | 2.0 | | |\n";

        LinkedHashSet<Mod> parsed = ModListTxtParser.parse(text);
        assertEquals(2, parsed.size(), "pre-notes table ignores jar-in-jar rows");
        Mod alpha = parsed.iterator().next();
        assertEquals(Boolean.TRUE, alpha.IsMCreator(), "pre-notes MCreator column");
        assertEquals(Boolean.TRUE, alpha.getIsLoadedByConnector(), "pre-notes Connector column");
    }

    private static void testDetailedLegacyFormat() throws Exception {
        String text =
                "Mods count: 2\n\n"
                        + "alpha.jar (MCreator mod) : alpha\n"
                        + "    mixins:\n"
                        + "        alpha.mixins.json\n"
                        + "    jarjar:\n"
                        + "        nested.jar : nested\n"
                        + "beta.jar : beta\n";

        LinkedHashSet<Mod> parsed = ModListTxtParser.parse(text);
        assertEquals(2, parsed.size(), "detailed legacy ignores metadata and jar-in-jar rows");
        Mod alpha = parsed.iterator().next();
        assertEquals("alpha.jar", alpha.getJarName(), "detailed legacy jar name");
        assertEquals("alpha", alpha.getModId(), "detailed legacy mod id");
        assertEquals(Boolean.TRUE, alpha.IsMCreator(), "detailed legacy MCreator marker");
    }

    private static void testSimpleLegacyFormat() throws Exception {
        String text = "alpha.jar : alpha\nloader 1.0 (modloader) : forge\n";
        LinkedHashSet<Mod> parsed = ModListTxtParser.parse(text);
        assertEquals(2, parsed.size(), "simple legacy count");
        assertEquals("alpha", parsed.iterator().next().getModId(), "simple legacy mod id");
    }

    private static void testRejectsCountMismatchAndUnrelatedText() throws Exception {
        expectFailure("Mods count: 2\n\nalpha.jar : alpha\n", "count mismatch");
        expectFailure("This is an arbitrary clipboard message\n", "unrelated clipboard text");
    }

    private static void expectFailure(String text, String label) throws Exception {
        try {
            ModListTxtParser.parse(text);
            throw new AssertionError(label + " should have failed");
        } catch (ModListTxtParser.ModListTxtParseException expected) {
            // Expected.
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
