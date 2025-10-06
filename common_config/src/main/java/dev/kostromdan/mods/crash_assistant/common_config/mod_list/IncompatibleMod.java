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
                "crashdetector",
                "crashdetector",
                "These mods perform very similar functions and are implemented in very similar ways.\n" +
                        "There is no reason to have both. Please remove one of them.\n" +
                        "Both mods consume RAM and system resources, and both display GUIs after a crash.\n" +
                        "It can be inconvenient to have multiple crash screens appear after a crash - from Crash Assistant, Crash Detector, launcher, etc.\n" +
                        "We (Crash Assistant developers) are aware of crashes caused by compatibility issues,\nbut we have no intention of fixing them as there is too much drama involved.\n" +
                        "As mentioned above, since the mods have very similar functionality and concepts,\nthere's no reason to have both. Please read the descriptions of both and choose the one that better suits your needs.\n" +
                        "You can disable the compatibility check in the Crash Assistant config, but be aware that you have been warned about potential issues and inconveniences!"
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
