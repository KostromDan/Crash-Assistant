package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class IncompatibleMod {
    private final String jarNamePart;
    private final String modId;
    private final String explainMessage;
    private final List<Mod> detectedMods = new ArrayList<>();
    public static final HashSet<IncompatibleMod> incompatibleMods = new HashSet<IncompatibleMod>() {{
        add(new IncompatibleMod(
                "crashdetectormc",
                "crashdetector",
                "These mods perform very similar functions and are implemented in very similar ways.\n" +
                        "There is no reason to have both. Please remove one of them.\n" +
                        "We (crash assistant developers) are aware of crashes caused by compatibility issues\nbut we have no intention of fixing them as there is too much drama involved.\n" +
                        "And as mentioned above, since the mods have very similar functionality and concepts,\nthere's no reason to have both. Read descriptions of both and choose one closer to your needs."
        ));
    }};


    public IncompatibleMod(String jarNamePart, String modId, String explainMessage) {
        this.jarNamePart = jarNamePart;
        this.modId = modId;
        this.explainMessage = explainMessage;
    }

    public String getJarNamePart() {
        return jarNamePart;
    }

    public String getModId() {
        return modId;
    }

    public String getExplainMessage() {
        return explainMessage;
    }

    public List<Mod> getDetectedMods() {
        return detectedMods;
    }

    public void addDetectedMod(Mod mod) {
        detectedMods.add(mod);
    }

    public void addDetectedMods(List<Mod> mods) {
        detectedMods.addAll(mods);
    }
}
