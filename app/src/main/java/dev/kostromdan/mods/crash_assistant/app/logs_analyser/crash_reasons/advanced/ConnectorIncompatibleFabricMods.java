package dev.kostromdan.mods.crash_assistant.app.logs_analyser.crash_reasons.advanced;

import dev.kostromdan.mods.crash_assistant.app.logs_analyser.KnownCrashReason;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.Log;
import dev.kostromdan.mods.crash_assistant.app.logs_analyser.LogType;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LanguageProvider;
import dev.kostromdan.mods.crash_assistant.common_config.lang.LinksProvider;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.Mod;
import dev.kostromdan.mods.crash_assistant.common_config.mod_list.ModListUtils;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Predicate;

public class ConnectorIncompatibleFabricMods extends KnownCrashReason {
    public ConnectorIncompatibleFabricMods() {
        super(
                LogType.LOG,
                LanguageProvider.get("warnings.connector_incompatible_fabric_mods", new HashMap<String, String>() {{
                    put("$LINK.CONNECTOR$", "Sinytra Connector");
                }})
        );
        this.priority = 3000;
    }

    @Override
    public boolean matches(Log log) {
        if (!PlatformHelp.isFabricBased()) return false;
        LinkedHashSet<Mod> mods = ModListUtils.getCurrentModList(true);
        if (mods.stream().noneMatch(ConnectorIncompatibleFabricMods::isConnectorMod)) return false;
        boolean triggered = false;
        for (IncompatibleFabricMods incompatibleFabricMod : IncompatibleFabricMods.values()) {
            Mod foundMod = mods.stream().filter(mod -> Objects.equals(mod.getModId(), incompatibleFabricMod.modId)).findFirst().orElse(null);
            if (foundMod == null) continue;
            if (!incompatibleFabricMod.isApplicable(foundMod)) continue;
            if (!triggered) message += "\n\n";
            triggered = true;
            String messageLine;
            if (incompatibleFabricMod.getModNameOfForgeNativeAnalogue() != null) {
                messageLine = LanguageProvider.get("warnings.connector_incompatible_fabric_mods_with_alternative");
                messageLine = messageLine.replace("$ALTERNATIVE$", "<a href='" + incompatibleFabricMod.getLinkToAnalogue() + "'>" + incompatibleFabricMod.getModNameOfForgeNativeAnalogue() + "</a>");
            } else {
                messageLine = LanguageProvider.get("warnings.connector_incompatible_fabric_mods_no_alternative");
            }
            messageLine = messageLine.replace("$MODID$", "<strong style='color: red;'>" + foundMod.getModId() + "</strong> (" + foundMod.getJarName() + ")");
            messageLine += "\n";
            message += messageLine;

        }
        if (triggered) message += "\n" + LanguageProvider.get("warnings.connector_incompatible_fabric_mods_end");

        return triggered;
    }

    private static boolean isConnectorMod(Mod mod) {
        if (mod.getJarJarMods().size() != 1) return false;
        return Objects.equals(mod.getJarJarMods().get(0).getModId(), "connectormod");
    }

    public enum IncompatibleFabricMods {
        SODIUM("sodium", "Embeddium", LinksProvider.EMBEDDIUM.getLink(), m -> PlatformHelp.platform == PlatformHelp.FORGE),
        IRIS("iris", "Oculus", LinksProvider.OCULUS.getLink(), m -> PlatformHelp.platform == PlatformHelp.FORGE),
        LITHIUM("lithium", "Radium", LinksProvider.RADIUM.getLink(), m -> PlatformHelp.platform == PlatformHelp.FORGE);

        private final String modId;
        private String modNameOfForgeNativeAnalogue = null;
        private String linkToAnalogue = null;
        private Predicate<Mod> isApplicable = x -> true;


        IncompatibleFabricMods(String modId) {
            this.modId = modId;
        }

        IncompatibleFabricMods(String modId, String modNameOfForgeNativeAnalogue, String linkToAnalogue) {
            this.modId = modId;
            this.modNameOfForgeNativeAnalogue = modNameOfForgeNativeAnalogue;
            this.linkToAnalogue = linkToAnalogue;
        }

        IncompatibleFabricMods(String modId, String modNameOfForgeNativeAnalogue, String linkToAnalogue, Predicate<Mod> isApplicable) {
            this.modId = modId;
            this.modNameOfForgeNativeAnalogue = modNameOfForgeNativeAnalogue;
            this.linkToAnalogue = linkToAnalogue;
            this.isApplicable = isApplicable;
        }

        public String getModId() {
            return modId;
        }

        public String getModNameOfForgeNativeAnalogue() {
            return modNameOfForgeNativeAnalogue;
        }

        public String getLinkToAnalogue() {
            return linkToAnalogue;
        }

        public boolean isApplicable(Mod mod) {
            return isApplicable.test(mod);
        }
    }
}
