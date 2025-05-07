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
                "Mods are doing very similar function and implemented very similar. Where is no reason to have both. Remove one of them.\n" +
                        "We know crashes caused by compatibility issues and we(crash assistant developers) have intent on fixing them since there is a lot of drama."
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
