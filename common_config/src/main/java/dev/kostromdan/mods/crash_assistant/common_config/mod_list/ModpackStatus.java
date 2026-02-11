package dev.kostromdan.mods.crash_assistant.common_config.mod_list;

/**
 * Represents the status of a mod relative to the saved modpack manifest.
 */
public enum ModpackStatus {
    /**
     * The mod (or its container file) exists in the modpack and the version matches.
     */
    UNCHANGED,

    /**
     * The mod (or its container file) exists in the modpack, but the version is different.
     */
    UPDATED,

    /**
     * The mod is currently installed but was NOT present in the saved modpack.
     */
    ADDED,

    /**
     * The mod was present in the saved modpack but is NOT currently installed.
     * Note: This works reliably for Root JARs. For Jar-in-Jar mods, this status
     * usually cannot be determined because JiJ IDs are not stored in the modpack manifest.
     */
    REMOVED,

    /**
     * Status cannot be determined (e.g., feature disabled, invalid ID, or mod never existed).
     */
    UNKNOWN
}
