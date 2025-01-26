package dev.kostromdan.mods.crash_assistant.mod_list;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

public class Mod {
    private final String jarName;
    private final String modId;
    private final String version;

    public static final Type TYPE = new TypeToken<LinkedHashSet<Mod>>() {
    }.getType();
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(TYPE, new Mod.ModAdapter())
            .setPrettyPrinting()
            .create();

    public Mod(String jarName, String modId, String version) {
        this.jarName = jarName;
        this.modId = modId;
        this.version = version;
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

    @Override
    public String toString() {
        return "Mod{" +
                "fileName='" + jarName + '\'' +
                ", modId='" + modId + '\'' +
                ", version='" + version + '\'' +
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
                for (JsonElement element : json.getAsJsonArray()) {
                    mods.add(new Mod(element.getAsString(), null, null));
                }
            } else if (json.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    String modId = obj.has("modId") ? obj.get("modId").getAsString() : null;
                    String version = obj.has("version") ? obj.get("version").getAsString() : null;
                    mods.add(new Mod(entry.getKey(), modId, version));
                }
            }
            return mods;
        }

        @Override
        public JsonElement serialize(LinkedHashSet<Mod> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject root = new JsonObject();
            for (Mod mod : src) {
                JsonObject modDetails = new JsonObject();
                if (mod.getModId() != null) {
                    modDetails.addProperty("modId", mod.getModId());
                }
                if (mod.getVersion() != null) {
                    modDetails.addProperty("version", mod.getVersion());
                }
                root.add(mod.getJarName(), modDetails);
            }
            return root;
        }
    }
}
