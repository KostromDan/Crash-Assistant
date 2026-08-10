package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

import dev.kostromdan.mods.crash_assistant.common_config.config.CrashAssistantConfig;
import dev.kostromdan.mods.crash_assistant.common_config.platform.PlatformHelp;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class to determine the {@link ModpackStatus} of mods.
 * Automatically loads data from {@link ModListUtils} on the first call.
 */
public class ModpackStatusChecker {

    private static ModpackStatusChecker instance;

    // Maps for O(1) access complexity
    private final Map<String, Mod> currentModsById;
    private final Map<String, Mod> currentModsByJarName;
    private final Map<String, Mod> savedModsById;
    private final Map<String, Mod> savedModsByJarName;

    /**
     * Maps a JiJ Mod ID to its Root Container Mod (the physical file in /mods).
     */
    private final Map<String, Mod> jijRootContainerMap;

    private final boolean isEnabled;
    private final boolean hasSavedData;

    /**
     * Private constructor for internal initialization.
     * Loads mod lists from ModListUtils and indexes them.
     */
    private ModpackStatusChecker() {
        // Saved modlist.json is a modpack manifest only when the instance uses
        // modpack support settings. Ordinary installations use launch history.
        this.isEnabled = CrashAssistantConfig.getBoolean("modpack_modlist.enabled")
                && !PlatformHelp.isLinkDefault();

        this.currentModsById = new HashMap<>();
        this.currentModsByJarName = new HashMap<>();
        this.savedModsById = new HashMap<>();
        this.savedModsByJarName = new HashMap<>();
        this.jijRootContainerMap = new HashMap<>();

        if (this.isEnabled) {
            // Load Current Mods (using cache if available via ModListUtils)
            LinkedHashSet<Mod> currentList = ModListUtils.getCurrentModList(true);
            if (currentList != null) {
                for (Mod rootMod : currentList) {
                    // Populate current maps for Root mods
                    if (rootMod.getModId() != null) {
                        currentModsById.put(rootMod.getModId(), rootMod);
                    }
                    if (rootMod.getJarName() != null) {
                        currentModsByJarName.put(rootMod.getJarName(), rootMod);
                    }

                    // Scan for Jar-in-Jars to build the parent relationship map
                    mapChildrenToRoot(rootMod, rootMod);
                }
            }

            // Load Saved Modpack Mods
            LinkedHashSet<Mod> savedList = ModListUtils.getSavedModList();
            this.hasSavedData = savedList != null && !savedList.isEmpty();

            if (this.hasSavedData) {
                for (Mod mod : savedList) {
                    // Populate saved maps
                    if (mod.getModId() != null) {
                        savedModsById.put(mod.getModId(), mod);
                    }
                    if (mod.getJarName() != null) {
                        savedModsByJarName.put(mod.getJarName(), mod);
                    }
                }
            }
        } else {
            this.hasSavedData = false;
        }
    }

    /**
     * Recursive helper to map all nested JiJ mods to their physical Root container.
     */
    private void mapChildrenToRoot(Mod current, Mod rootContainer) {
        List<Mod> children = current.getJarJarMods();
        if (children == null || children.isEmpty()) return;

        for (Mod child : children) {
            if (child.getModId() != null) {
                // Determine logic:
                // 1. We map the child ID to the actual object for lookups
                currentModsById.put(child.getModId(), child);
                // 2. We record who the physical owner (root) is
                jijRootContainerMap.put(child.getModId(), rootContainer);
            }
            // Recurse deeper (JiJ inside JiJ)
            mapChildrenToRoot(child, rootContainer);
        }
    }

    /**
     * Lazy singleton initialization.
     */
    private static synchronized ModpackStatusChecker getInstance() {
        if (instance == null) {
            instance = new ModpackStatusChecker();
        }
        return instance;
    }

    /**
     * Checks the status of a mod by its Mod ID.
     * Handles Jar-in-Jar logic automatically by checking the parent container.
     *
     * @param modId The mod ID to check.
     * @return The {@link ModpackStatus}.
     */
    public static ModpackStatus getStatusById(String modId) {
        if (modId == null) return ModpackStatus.UNKNOWN;
        return getInstance().resolveStatusById(modId);
    }

    /**
     * Checks the status of a mod by its JAR file name.
     *
     * @param jarName The JAR file name to check.
     * @return The {@link ModpackStatus}.
     */
    public static ModpackStatus getStatusByFileName(String jarName) {
        if (jarName == null) return ModpackStatus.UNKNOWN;
        return getInstance().resolveStatusByFileName(jarName);
    }

    // ================= Internal Logic =================

    private ModpackStatus resolveStatusById(String modId) {
        if (!isEnabled || !hasSavedData) {
            return ModpackStatus.UNKNOWN;
        }

        Mod currentMod = currentModsById.get(modId);

        // CASE 1: Mod is NOT installed currently.
        if (currentMod == null) {
            // Check if it WAS in the modpack (Root mods only).
            // We cannot reliably detect removed JiJ mods by ID because JiJ IDs are not stored in saved modpack.
            if (savedModsById.containsKey(modId)) {
                return ModpackStatus.REMOVED;
            }
            return ModpackStatus.UNKNOWN;
        }

        // CASE 2: Mod IS installed. Check if it is a Jar-in-Jar.
        // We use our manually built map instead of getParentJar()
        Mod rootContainer = jijRootContainerMap.get(modId);

        if (rootContainer != null) {
            // It is a JiJ. Delegate check to its Root Container.
            return resolveJarInJarStatus(rootContainer);
        }

        // CASE 3: Standard Root Mod.
        Mod savedMod = savedModsById.get(modId);

        if (savedMod == null) {
            return ModpackStatus.ADDED;
        }

        // Compare versions
        if (Objects.equals(currentMod.getVersion(), savedMod.getVersion())) {
            return ModpackStatus.UNCHANGED;
        } else {
            return ModpackStatus.UPDATED;
        }
    }

    private ModpackStatus resolveStatusByFileName(String jarName) {
        if (!isEnabled || !hasSavedData) {
            return ModpackStatus.UNKNOWN;
        }

        Mod currentMod = currentModsByJarName.get(jarName);
        Mod savedMod = savedModsByJarName.get(jarName);

        // CASE 1: File is missing now.
        if (currentMod == null) {
            if (savedMod != null) {
                return ModpackStatus.REMOVED;
            }
            return ModpackStatus.UNKNOWN;
        }

        // CASE 2: File exists now, but wasn't in modpack.
        if (savedMod == null) {
            return ModpackStatus.ADDED;
        }

        // CASE 3: File exists in both. Compare versions.
        if (Objects.equals(currentMod.getVersion(), savedMod.getVersion())) {
            return ModpackStatus.UNCHANGED;
        } else {
            return ModpackStatus.UPDATED;
        }
    }

    /**
     * Logic for Mods that are inside other Jars (Jar-in-Jar).
     * We track the status of the PARENT container.
     *
     * @param currentRootContainer The physical JAR file mod that contains the JiJ.
     */
    private ModpackStatus resolveJarInJarStatus(Mod currentRootContainer) {
        Mod savedParent = null;

        // 1. Try to find the container in the saved modpack by Mod ID (Most reliable)
        // Example: "create" ID exists in both lists, even if filename changed from create-6.0.6.jar to create-6.0.8.jar
        if (currentRootContainer.getModId() != null) {
            savedParent = savedModsById.get(currentRootContainer.getModId());
        }

        // 2. Fallback: Try by JAR filename (e.g. for libraries or mods without IDs)
        if (savedParent == null && currentRootContainer.getJarName() != null) {
            savedParent = savedModsByJarName.get(currentRootContainer.getJarName());
        }

        // If neither ID nor Filename matched in the old list -> The container is new.
        if (savedParent == null) {
            return ModpackStatus.ADDED;
        }

        // The parent file existed. Now compare versions.
        if (Objects.equals(savedParent.getVersion(), currentRootContainer.getVersion())) {
            // Parent container is identical -> Internal mod is considered unchanged.
            return ModpackStatus.UNCHANGED;
        } else {
            // Parent container version changed -> Internal mod is considered updated.
            return ModpackStatus.UPDATED;
        }
    }
}
