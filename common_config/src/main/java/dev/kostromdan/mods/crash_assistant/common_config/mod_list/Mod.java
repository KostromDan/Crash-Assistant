package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

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
    private final String version;
    private final Boolean isMCreator;
    private final List<String> mixinConfigs;
    private final List<Mod> jarJarMods;

    public static final Type TYPE = new TypeToken<LinkedHashSet<Mod>>() {
    }.getType();
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(TYPE, new Mod.ModAdapter())
            .setPrettyPrinting()
            .create();

    public Mod(String jarName, String modId, String version, Boolean isMCreator, List<String> mixinConfigs, List<Mod> jarJarMods) {
        this.jarName = jarName;
        this.modId = modId;
        this.version = version;
        this.isMCreator = isMCreator;
        this.mixinConfigs = mixinConfigs;
        this.jarJarMods = jarJarMods;
    }

    public String getJarName() {
        return jarName;
    }

    public String getModId() {
        return modId;
    }

    public String getVersion() {
        return version;
    }

    public Boolean IsMCreator() {
        return isMCreator;
    }

    public List<String> getMixinConfigs() {
        return mixinConfigs;
    }

    public List<Mod> getJarJarMods() {
        return jarJarMods;
    }

    /**
     * Writes a list of mods to a text file with detailed formatting.
     * 
     * @param modListTxtPath Path to the output file
     * @param mods Collection of mods to write
     * @throws IOException If an I/O error occurs
     */
    public static void writeModlistTxt(Path modListTxtPath, Collection<Mod> mods) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(modListTxtPath, StandardCharsets.UTF_8)) {
            writer.write("Mods count: " + mods.size() + "\n\n");
            for (Mod mod : mods) {
                writeModWithFormatting(writer, mod, 0);
            }
        }
    }

    /**
     * Helper method to write a single mod with proper indentation and formatting.
     * Handles recursive formatting for jarJarMods.
     * 
     * @param writer The BufferedWriter to write to
     * @param mod The mod to write
     * @param indentLevel The current indentation level (0 for top-level mods)
     * @throws IOException If an I/O error occurs
     */
    private static void writeModWithFormatting(BufferedWriter writer, Mod mod, int indentLevel) throws IOException {
        // Create indentation string based on level
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indentBuilder.append("        ");
        }
        String indent = indentBuilder.toString();

        // Write jar name
        writer.write(indent + mod.getJarName());

        // Add mod ID if available
        if (mod.getModId() != null) {
            writer.write(" : " + mod.getModId());
        }

        // Add MCreator indicator if applicable
        if (mod.IsMCreator() != null && mod.IsMCreator()) {
            writer.write(" (MCreator mod)");
        }

        writer.newLine();

        // Write mixin configs if any
        if (mod.getMixinConfigs() != null && !mod.getMixinConfigs().isEmpty()) {
            writer.write(indent + "    mixins:");
            writer.newLine();
            for (String mixin : mod.getMixinConfigs()) {
                writer.write(indent + "        " + mixin);
                writer.newLine();
            }
        }

        // Write jarJarMods if any
        if (mod.getJarJarMods() != null && !mod.getJarJarMods().isEmpty()) {
            writer.write(indent + "    jarjar:");
            writer.newLine();

            // Recursively write each jarJarMod with increased indentation
            for (Mod jarJarMod : mod.getJarJarMods()) {
                writeModWithFormatting(writer, jarJarMod, indentLevel + 1);
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
                ", version='" + version + '\'' +
                ", isMCreator='" + isMCreator + '\'' +
                ", mixinConfigs='" + mixinConfigs + '\'' +
                ", jarJarMods='" + jarJarMods + '\'' +
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

    public static class ModAdapter implements JsonDeserializer<LinkedHashSet<Mod>>, JsonSerializer<LinkedHashSet<Mod>> {
        @Override
        public LinkedHashSet<Mod> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            LinkedHashSet<Mod> mods = new LinkedHashSet<>();
            if (json.isJsonArray()) {
                // Simple array format with just jar names
                for (JsonElement element : json.getAsJsonArray()) {
                    mods.add(new Mod(element.getAsString(), null, null, null, new ArrayList<>(), new ArrayList<>()));
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
                return new Mod(element.getAsString(), null, null, null, new ArrayList<>(), new ArrayList<>());
            } else if (element.isJsonObject()) {
                // Object format with full mod details
                JsonObject modObj = element.getAsJsonObject();
                String jarName = modObj.has("jarName") ? modObj.get("jarName").getAsString() : null;
                return deserializeModObject(modObj, jarName);
            }

            // Default case (shouldn't happen with well-formed JSON)
            return new Mod("unknown", null, null, null, new ArrayList<>(), new ArrayList<>());
        }

        /**
         * Extracts mod properties from a JSON object and creates a Mod instance.
         * Used by both top-level and nested mod deserialization.
         */
        private Mod deserializeModObject(JsonObject modObj, String jarName) {
            // Extract basic properties
            String modId = modObj.has("modId") ? modObj.get("modId").getAsString() : null;
            String version = modObj.has("version") ? modObj.get("version").getAsString() : null;
            Boolean isMCreator = modObj.has("isMCreator") ? modObj.get("isMCreator").getAsBoolean() : null;

            // Parse mixinConfigs
            List<String> mixinConfigs = new ArrayList<>();
            if (modObj.has("mixinConfigs") && modObj.get("mixinConfigs").isJsonArray()) {
                JsonArray mixinArray = modObj.get("mixinConfigs").getAsJsonArray();
                for (JsonElement mixinElement : mixinArray) {
                    mixinConfigs.add(mixinElement.getAsString());
                }
            }

            // Parse nested jarJarMods recursively
            List<Mod> jarJarMods = new ArrayList<>();
            if (modObj.has("jarJarMods") && modObj.get("jarJarMods").isJsonArray()) {
                JsonArray jarJarArray = modObj.get("jarJarMods").getAsJsonArray();
                for (JsonElement jarJarElement : jarJarArray) {
                    jarJarMods.add(deserializeMod(jarJarElement));
                }
            }

            return new Mod(jarName, modId, version, isMCreator, mixinConfigs, jarJarMods);
        }

        @Override
        public JsonElement serialize(LinkedHashSet<Mod> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject root = new JsonObject();
            for (Mod mod : src) {
                // For each mod, create its JSON representation but with jarName as key in the root object
                JsonObject modDetails = createModJsonObject(mod, false); // false = don't include jarName as property
                root.add(mod.getJarName(), modDetails);
            }
            return root;
        }

        /**
         * Creates a JSON object with all the properties of a Mod.
         * @param mod The mod to serialize
         * @param includeJarName Whether to include jarName as a property (true for nested mods, false for root mods)
         * @return JsonObject representing the mod
         */
        private JsonObject createModJsonObject(Mod mod, boolean includeJarName) {
            JsonObject modObj = new JsonObject();

            // Add jarName only for nested mods (not for root mods where it's the key)
            if (includeJarName && mod.getJarName() != null) {
                modObj.addProperty("jarName", mod.getJarName());
            }

            // Add basic properties
            if (mod.getModId() != null) {
                modObj.addProperty("modId", mod.getModId());
            }
            if (mod.getVersion() != null) {
                modObj.addProperty("version", mod.getVersion());
            }
            if (mod.IsMCreator() != null) {
                modObj.addProperty("isMCreator", mod.IsMCreator());
            }

            // Add mixinConfigs if any
            if (mod.getMixinConfigs() != null && !mod.getMixinConfigs().isEmpty()) {
                JsonArray mixinArray = new JsonArray();
                for (String mixin : mod.getMixinConfigs()) {
                    mixinArray.add(mixin);
                }
                modObj.add("mixinConfigs", mixinArray);
            }

            // Add nested jarJarMods recursively
            if (mod.getJarJarMods() != null && !mod.getJarJarMods().isEmpty()) {
                JsonArray jarJarArray = new JsonArray();
                for (Mod nestedMod : mod.getJarJarMods()) {
                    // Create JsonObject for nested mod, including its jarName as property
                    JsonObject nestedModObj = createModJsonObject(nestedMod, true);
                    jarJarArray.add(nestedModObj);
                }
                modObj.add("jarJarMods", jarJarArray);
            }

            return modObj;
        }
    }
}
