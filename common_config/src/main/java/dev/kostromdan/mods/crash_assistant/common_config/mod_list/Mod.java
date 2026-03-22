package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.jexl3.annotations.NoJexl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Mod {
    private final String jarName;
    private final String modId;
    private final String name;
    private final String version;
    private final Boolean isMCreator;
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
    public Mod(String jarName, String modId, String name, String version, Boolean isMCreator, HashSet<String> mixinConfigs, List<Mod> jarJarMods, String pathFromJarJar) {
        this(jarName, modId, name, version, isMCreator, mixinConfigs, jarJarMods, pathFromJarJar, null, null);
    }

    @NoJexl
    public Mod(String jarName, String modId, String name, String version, Boolean isMCreator, HashSet<String> mixinConfigs, List<Mod> jarJarMods, String pathFromJarJar, Long curseForgeHash, String modrinthHash) {
        this.jarName = jarName;
        this.modId = modId;
        this.name = name;
        this.version = version;
        this.isMCreator = isMCreator;
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
        try (BufferedWriter writer = Files.newBufferedWriter(modListTxtPath, StandardCharsets.UTF_8)) {
            writer.write("Mods count: " + mods.size() + "\n \n");

            Mod tableColumnNames = new Mod("jar name", "mod id (isMCreator)", "mod name", null, null, new HashSet<String>() {{
                add("mixin configs");
            }}, new ArrayList<>(), "");
            List<Mod> finalMods = new ArrayList<Mod>() {{
                add(tableColumnNames);
                addAll(mods);
            }};
            int[] maxLens = computeMaxLengths(finalMods, 0);
            int maxJarNameLength = maxLens[0];
            int maxModIdLength = maxLens[1];
            int maxNameLength = maxLens[2];

            for (Mod mod : finalMods) {
                writeModWithFormatting(writer, mod, 0, maxJarNameLength, maxModIdLength, maxNameLength);
            }
        }
    }

    private static int[] computeMaxLengths(Collection<Mod> mods, int indentLevel) {
        int maxJarLen = 0;
        int maxModIdLen = 0;
        int maxNameLen = 0;

        for (Mod mod : mods) {
            // ── protect against nulls ───────────────────────────────────────
            String jarName = mod.getJarName() != null ? mod.getJarName() : "";
            String pathFromJarJar = mod.getPathFromJarJar() != null ? mod.getPathFromJarJar() : "";
            String modId = mod.getModId() != null ? mod.getModId() : "";
            String name = mod.getName() != null ? mod.getName() : "";

            /* jar column: 4 × indent + jarName + pathFromJarJar */
            int jarLen = indentLevel * 4 + jarName.length() + pathFromJarJar.length();
            maxJarLen = Math.max(maxJarLen, jarLen);

            /* mod-id column: 4 × indent + modId + optional “ (MCreator mod)” */
            int modIdLen = modId.length();
            if (Boolean.TRUE.equals(mod.IsMCreator())) {
                modIdLen += " (MCreator mod)".length();
            }
            maxModIdLen = Math.max(maxModIdLen, modIdLen);

            /* name column: 4 × indent + name */
            int nameLen = name.length();
            maxNameLen = Math.max(maxNameLen, nameLen);

            /* recurse into nested mods, if any */
            if (mod.getJarJarMods() != null && !mod.getJarJarMods().isEmpty()) {
                int[] childLens = computeMaxLengths(mod.getJarJarMods(), indentLevel + 1);
                maxJarLen = Math.max(maxJarLen, childLens[0]);
                maxModIdLen = Math.max(maxModIdLen, childLens[1]);
                maxNameLen = Math.max(maxNameLen, childLens[2]);
            }
        }
        return new int[]{maxJarLen, maxModIdLen, maxNameLen};
    }

    /**
     * Helper method to write a single mod with proper indentation and formatting.
     * Handles recursive formatting for jarJarMods.
     *
     * @param writer      The BufferedWriter to write to
     * @param mod         The mod to write
     * @param indentLevel The current indentation level (0 for top-level mods)
     * @throws IOException If an I/O error occurs
     */
    private static void writeModWithFormatting(BufferedWriter writer, Mod mod, int indentLevel, int maxJarNameLength, int maxModIdLength, int maxNameLength) throws IOException {
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indentBuilder.append("    ");
        }
        String indent = indentBuilder.toString();

        StringBuilder line = new StringBuilder();
        line.append(String.format("%-" + maxJarNameLength + "s", indent + (mod.getPathFromJarJar() != null ? mod.getPathFromJarJar() : "") + mod.getJarName()));

        String mCreatorString = mod.IsMCreator() != null && mod.IsMCreator() ? " (MCreator mod)" : "";

        line.append(" | ").append(String.format("%-" + maxModIdLength + "s", (mod.getModId() == null ? "" : mod.getModId()) + mCreatorString));

        line.append(" | ").append(String.format("%-" + maxNameLength + "s", (mod.getName() == null ? "" : mod.getName())));

        line.append(" | ").append(String.join(", ", mod.getMixinConfigs() == null ? new HashSet<>() : mod.getMixinConfigs()));

        writer.write(line.toString());
        writer.newLine();

        if (mod.getJarJarMods() != null && !mod.getJarJarMods().isEmpty()) {
            for (Mod jarJarMod : mod.getJarJarMods()) {
                writeModWithFormatting(writer, jarJarMod, indentLevel + 1, maxJarNameLength, maxModIdLength, maxNameLength);
            }
        }
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
                    mods.add(new Mod(element.getAsString(), null, null, null, null, new HashSet<>(), new ArrayList<>(), null));
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
                return new Mod(element.getAsString(), null, null, null, null, new HashSet<>(), new ArrayList<>(), null);
            } else if (element.isJsonObject()) {
                // Object format with full mod details
                JsonObject modObj = element.getAsJsonObject();
                String jarName = modObj.has("jarName") ? modObj.get("jarName").getAsString() : null;
                return deserializeModObject(modObj, jarName);
            }

            // Default case (shouldn't happen with well-formed JSON)
            return new Mod("unknown", null, null, null, null, new HashSet<>(), new ArrayList<>(), null);
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

            return new Mod(jarName, modId, name, version, null, new HashSet<>(), new ArrayList<>(), null, curseForgeHash, modrinthHash);
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
