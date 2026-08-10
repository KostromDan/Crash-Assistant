package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Mod {
    private final String jarName;
    private final String modId;
    private final String name;
    private final String version;
    private final Boolean isMCreator;
    private final Boolean isLoadedByConnector;
    private final HashSet<String> mixinConfigs;
    private final List<Mod> jarJarMods;
    private final String pathFromJarJar;
    private final Long curseForgeHash;
    private final String modrinthHash;

    public static final Type TYPE = new TypeToken<LinkedHashSet<Mod>>() {
    }.getType();

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(TYPE, new Mod.ModAdapter())
            .setPrettyPrinting()
            .create();

    @NoJexl
    public Mod(String jarName) {
        this(jarName, null, null, null, null, null, new HashSet<>(), new ArrayList<>(), null, null,null);;
    }

    @NoJexl
    public Mod(String jarName, String modId, String name, String version, Boolean isMCreator, Boolean isLoadedByConnector, HashSet<String> mixinConfigs, List<Mod> jarJarMods, String pathFromJarJar, Long curseForgeHash, String modrinthHash) {
        this.jarName = jarName;
        this.modId = modId;
        this.name = name;
        this.version = version;
        this.isMCreator = isMCreator;
        this.isLoadedByConnector = isLoadedByConnector;
        this.mixinConfigs = mixinConfigs;
        this.jarJarMods = jarJarMods;
        this.pathFromJarJar = pathFromJarJar;
        this.curseForgeHash = curseForgeHash;
        this.modrinthHash = modrinthHash;
    }

    public String getJarName() {
        return jarName;
    }

    public String getModId() {
        return modId;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Boolean IsMCreator() {
        return isMCreator;
    }

    public Boolean getIsLoadedByConnector() {
        return isLoadedByConnector;
    }

    public HashSet<String> getMixinConfigs() {
        return mixinConfigs;
    }

    public List<Mod> getJarJarMods() {
        return jarJarMods;
    }

    public String getPathFromJarJar() {
        return pathFromJarJar;
    }

    public Long getCurseForgeHash() {
        return curseForgeHash;
    }

    public String getModrinthHash() {
        return modrinthHash;
    }

    /**
     * Writes a list of mods to a text file with detailed formatting.
     *
     * @param modListTxtPath Path to the output file
     * @param mods           Collection of mods to write
     * @throws IOException If an I/O error occurs
     */
    @NoJexl
    public static void writeModlistTxt(Path modListTxtPath, Collection<Mod> mods) throws IOException {
        Path absoluteTarget = modListTxtPath.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("modlist.txt has no parent directory: " + modListTxtPath);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".modlist-", ".tmp");
        try {
            writeModlistTxtDirect(temporary, mods);
            try {
                Files.move(temporary, absoluteTarget,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeModlistTxtDirect(Path modListTxtPath, Collection<Mod> mods) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(modListTxtPath, StandardCharsets.UTF_8)) {
            writer.write("Mods count: " + mods.size() + "\n \n");

            Mod tableColumnNames = new Mod("jar name", "mod id", "mod name", "mod version", null, null, new HashSet<String>() {{
                add("mixin configs");
            }}, new ArrayList<>(), "", null, "modrinth hash");

            List<Mod> finalMods = new ArrayList<Mod>() {{
                add(tableColumnNames);
                addAll(mods);
            }};
            int[] maxLens = computeMaxLengths(finalMods, 0);

            for (int i = 0; i < finalMods.size(); i++) {
                writeModWithFormatting(writer, finalMods.get(i), 0, maxLens);
                if (i == 0) {
                    writeSeparator(writer, maxLens);
                }
            }
        }
    }

    private static void writeSeparator(BufferedWriter writer, int[] maxLens) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLens.length; i++) {
            for (int j = 0; j < maxLens[i]; j++) {
                sb.append('-');
            }
            if (i < maxLens.length - 1) {
                sb.append("-+-");
            }
        }
        writer.write(sb.toString());
        writer.newLine();
    }

    private static int[] computeMaxLengths(Collection<Mod> mods, int indentLevel) {
        int maxJarLen = 0;
        int maxNotesLen = 0;
        int maxModIdLen = 0;
        int maxNameLen = 0;
        int maxVersionLen = 0;
        int maxMixinConfigsLen = 0;
        int maxModrinthHashLen = 0;
        int maxCurseForgeHashLen = 0;

        for (Mod mod : mods) {
            // ── protect against nulls ───────────────────────────────────────
            String jarName = mod.getJarName() != null ? mod.getJarName() : "";
            String pathFromJarJar = mod.getPathFromJarJar() != null ? mod.getPathFromJarJar() : "";
            String modId = mod.getModId() != null ? mod.getModId() : "";
            String name = mod.getName() != null ? mod.getName() : "";
            String version = mod.getVersion() != null ? mod.getVersion() : "";
            String mixinConfigs = String.join(", ", mod.getMixinConfigs() == null ? new HashSet<>() : mod.getMixinConfigs());
            String modrinthHash = mod.getModrinthHash() != null ? mod.getModrinthHash() : "";
            String curseForgeHash = mod.getCurseForgeHash() != null ? String.valueOf(mod.getCurseForgeHash()) : "";

            if (jarName.equals("jar name")) {
                curseForgeHash = "curseforge hash";
            }

            String notes = "";
            if (jarName.equals("jar name")) {
                notes = "notes";
            } else {
                List<String> notesList = new ArrayList<>();
                if (Boolean.TRUE.equals(mod.IsMCreator())) notesList.add("MCreator mod");
                if (Boolean.TRUE.equals(mod.getIsLoadedByConnector())) notesList.add("via Connector");
                notes = String.join(", ", notesList);
            }

            /* jar column: 4 × indent + jarName + pathFromJarJar */
            int jarLen = indentLevel * 4 + jarName.length() + pathFromJarJar.length();
            maxJarLen = Math.max(maxJarLen, jarLen);
            maxNotesLen = Math.max(maxNotesLen, notes.length());
            maxModIdLen = Math.max(maxModIdLen, modId.length());
            maxNameLen = Math.max(maxNameLen, name.length());
            maxVersionLen = Math.max(maxVersionLen, version.length());
            maxMixinConfigsLen = Math.max(maxMixinConfigsLen, mixinConfigs.length());
            maxModrinthHashLen = Math.max(maxModrinthHashLen, modrinthHash.length());
            maxCurseForgeHashLen = Math.max(maxCurseForgeHashLen, curseForgeHash.length());

            /* recurse into nested mods, if any */
            if (mod.getJarJarMods() != null && !mod.getJarJarMods().isEmpty()) {
                int[] childLens = computeMaxLengths(mod.getJarJarMods(), indentLevel + 1);
                maxJarLen = Math.max(maxJarLen, childLens[0]);
                maxNotesLen = Math.max(maxNotesLen, childLens[1]);
                maxModIdLen = Math.max(maxModIdLen, childLens[2]);
                maxNameLen = Math.max(maxNameLen, childLens[3]);
                maxVersionLen = Math.max(maxVersionLen, childLens[4]);
                maxMixinConfigsLen = Math.max(maxMixinConfigsLen, childLens[5]);
                maxModrinthHashLen = Math.max(maxModrinthHashLen, childLens[6]);
                maxCurseForgeHashLen = Math.max(maxCurseForgeHashLen, childLens[7]);
            }
        }
        return new int[]{maxJarLen, maxNotesLen, maxModIdLen, maxNameLen, maxVersionLen, maxMixinConfigsLen, maxModrinthHashLen, maxCurseForgeHashLen};
    }

    /**
     * Helper method to write a single mod with proper indentation and formatting.
     * Handles recursive formatting for jarJarMods.
     *
     * @param writer      The BufferedWriter to write to
     * @param mod         The mod to write
     * @param indentLevel The current indentation level (0 for top-level mods)
     * @param maxLens     The maximum lengths for each column
     * @throws IOException If an I/O error occurs
     */
    private static void writeModWithFormatting(BufferedWriter writer, Mod mod, int indentLevel, int[] maxLens) throws IOException {
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indentBuilder.append("    ");
        }
        String indent = indentBuilder.toString();

        int maxJarNameLength = maxLens[0];
        int maxNotesLength = maxLens[1];
        int maxModIdLength = maxLens[2];
        int maxNameLength = maxLens[3];
        int maxVersionLength = maxLens[4];
        int maxMixinConfigsLength = maxLens[5];
        int maxModrinthHashLength = maxLens[6];
        int maxCurseForgeHashLength = maxLens[7];

        StringBuilder line = new StringBuilder();

        String jarName = mod.getJarName() != null ? mod.getJarName() : "";
        String curseForgeHash = mod.getCurseForgeHash() != null ? String.valueOf(mod.getCurseForgeHash()) : "";
        if (jarName.equals("jar name")) {
            curseForgeHash = "curseforge hash";
        }
        String notes = "";
        boolean isHeader = jarName.equals("jar name");
        if (isHeader) {
            notes = "notes";
        } else {
            List<String> notesList = new ArrayList<>();
            if (Boolean.TRUE.equals(mod.IsMCreator())) notesList.add("MCreator mod");
            if (Boolean.TRUE.equals(mod.getIsLoadedByConnector())) notesList.add("via Connector");
            notes = String.join(", ", notesList);
        }

        line.append(formatCell(indent + (mod.getPathFromJarJar() != null ? mod.getPathFromJarJar() : "") + jarName, maxJarNameLength, isHeader));
        line.append(" | ").append(formatCell(notes, maxNotesLength, isHeader));
        line.append(" | ").append(formatCell((mod.getModId() == null ? "" : mod.getModId()), maxModIdLength, isHeader));
        line.append(" | ").append(formatCell((mod.getName() == null ? "" : mod.getName()), maxNameLength, isHeader));
        line.append(" | ").append(formatCell((mod.getVersion() == null ? "" : mod.getVersion()), maxVersionLength, isHeader));
        line.append(" | ").append(formatCell(String.join(", ", mod.getMixinConfigs() == null ? new HashSet<>() : mod.getMixinConfigs()), maxMixinConfigsLength, isHeader));
        line.append(" | ").append(formatCell((mod.getModrinthHash() == null ? "" : mod.getModrinthHash()), maxModrinthHashLength, isHeader));
        line.append(" | ").append(formatCell(curseForgeHash, maxCurseForgeHashLength, isHeader));

        writer.write(line.toString());
        writer.newLine();

        if (mod.getJarJarMods() != null && !mod.getJarJarMods().isEmpty()) {
            for (Mod jarJarMod : mod.getJarJarMods()) {
                writeModWithFormatting(writer, jarJarMod, indentLevel + 1, maxLens);
            }
        }
    }

    private static String formatCell(String text, int width, boolean center) {
        if (text == null) text = "";
        if (!center) {
            return String.format("%-" + width + "s", text);
        }
        int textLength = text.length();
        if (textLength >= width) return text;
        int leftPadding = (width - textLength) / 2;
        int rightPadding = width - textLength - leftPadding;
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < leftPadding; i++) sb.append(' ');
        sb.append(text);
        for (int i = 0; i < rightPadding; i++) sb.append(' ');
        return sb.toString();
    }

    public boolean isModMessedUpWithVersion() {
        if (version == null || modId == null) {
            return true;
        }

        String normVersion = version.toLowerCase()
                .replaceAll("mc\\d+(\\.\\d+)*", "")
                .replaceAll("fabric|neo|forge", "")
                .replaceAll("[+._\\-]", "")
                .trim();
        String normJarName = jarName.toLowerCase()
                .replaceAll("mc\\d+(\\.\\d+)*", "")
                .replaceAll("fabric|neo|forge", "")
                .replaceAll("[+._\\-]", "")
                .trim();
        ;

        return !normJarName.contains(normVersion);
    }

    @Override
    public String toString() {
        return "Mod{" +
                "fileName='" + jarName + '\'' +
                ", modId='" + modId + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", curseForgeHash='" + curseForgeHash + '\'' +
                ", modrinthHash='" + modrinthHash + '\'' +
                ", isMCreator='" + isMCreator + '\'' +
                ", isLoadedByConnector='" + isLoadedByConnector + '\'' +
                ", mixinConfigs='" + mixinConfigs + '\'' +
                ", jarJarMods='" + jarJarMods + '\'' +
                ", pathFromJarJar='" + pathFromJarJar + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mod mod = (Mod) o;
        return Objects.equals(jarName, mod.jarName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jarName);
    }

    @NoJexl
    public static class ModAdapter implements JsonDeserializer<LinkedHashSet<Mod>>, JsonSerializer<LinkedHashSet<Mod>> {
        @Override
        public LinkedHashSet<Mod> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            LinkedHashSet<Mod> mods = new LinkedHashSet<>();
            if (json.isJsonArray()) {
                // Simple array format with just jar names
                for (JsonElement element : json.getAsJsonArray()) {
                    mods.add(new Mod(element.getAsString()));
                }
            } else if (json.isJsonObject()) {
                // Object format with detailed mod information
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                    String jarName = entry.getKey();
                    JsonObject modObj = entry.getValue().getAsJsonObject();

                    // Create Mod with jarName from entry key and other properties from the JSON object
                    Mod mod = deserializeModObject(modObj, jarName);
                    mods.add(mod);
                }
            }
            return mods;
        }

        /**
         * Deserializes a JsonElement into a Mod object, handling both string and object formats
         * and supporting recursive nested jarJarMods of any depth.
         */
        private Mod deserializeMod(JsonElement element) {
            if (element.isJsonPrimitive()) {
                // Legacy format: just a string with jar name
                return new Mod(element.getAsString());
            } else if (element.isJsonObject()) {
                // Object format with full mod details
                JsonObject modObj = element.getAsJsonObject();
                String jarName = modObj.has("jarName") ? modObj.get("jarName").getAsString() : null;
                return deserializeModObject(modObj, jarName);
            }

            // Default case (shouldn't happen with well-formed JSON)
            return new Mod("unknown");
        }

        /**
         * Extracts mod properties from a JSON object and creates a Mod instance.
         * Used by both top-level and nested mod deserialization.
         */
        private Mod deserializeModObject(JsonObject modObj, String jarName) {
            // Extract basic properties
            String modId = modObj.has("modId") ? modObj.get("modId").getAsString() : null;
            String version = modObj.has("version") ? modObj.get("version").getAsString() : null;
            String name = modObj.has("name") ? modObj.get("name").getAsString() : null;
            Long curseForgeHash = null;
            if (modObj.has("curseForgeHash") && !modObj.get("curseForgeHash").isJsonNull()) {
                curseForgeHash = modObj.get("curseForgeHash").getAsLong();
            }
            String modrinthHash = modObj.has("modrinthHash") && !modObj.get("modrinthHash").isJsonNull()
                    ? modObj.get("modrinthHash").getAsString()
                    : null;

            return new Mod(jarName, modId, name, version, null, null, new HashSet<>(), new ArrayList<>(), null, curseForgeHash, modrinthHash);
        }

        @Override
        public JsonElement serialize(LinkedHashSet<Mod> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject root = new JsonObject();
            for (Mod mod : src) {
                JsonObject modDetails = createModJsonObject(mod);
                root.add(mod.getJarName(), modDetails);
            }
            return root;
        }

        /**
         * Creates a JSON object with all the properties of a Mod.
         *
         * @param mod The mod to serialize
         * @return JsonObject representing the mod
         */
        private JsonObject createModJsonObject(Mod mod) {
            JsonObject modObj = new JsonObject();

            // Add jarName only for nested mods (not for root mods where it's the key)
            if (mod.getJarName() != null) {
                modObj.addProperty("jarName", mod.getJarName());
            }

            // Add basic properties
            if (mod.getModId() != null) {
                modObj.addProperty("modId", mod.getModId());
            }
            if (mod.getName() != null) {
                modObj.addProperty("name", mod.getName());
            }
            if (mod.getVersion() != null) {
                modObj.addProperty("version", mod.getVersion());
            }
            if (mod.getCurseForgeHash() != null) {
                modObj.addProperty("curseForgeHash", mod.getCurseForgeHash());
            }
            if (mod.getModrinthHash() != null) {
                modObj.addProperty("modrinthHash", mod.getModrinthHash());
            }
            return modObj;
        }
    }
}
