package dev.kostromdan.mods.crash_assistant.common_config.utils.maven_version_cmp;

/**
 * Utility methods for comparing version strings using Maven's ComparableVersion.
 */
public final class VersionUtils {

    private VersionUtils() {
        // Prevent instantiation
    }

    /**
     * Returns true if version1 is greater than or equal to version2.
     */
    public static boolean isGreaterThanOrEqual(String version1, String version2) {
        ComparableVersion v1 = new ComparableVersion(version1);
        ComparableVersion v2 = new ComparableVersion(version2);
        return v1.compareTo(v2) >= 0;
    }

    /**
     * Returns true if version1 is less than or equal to version2.
     */
    public static boolean isLowerEqual(String version1, String version2) {
        ComparableVersion v1 = new ComparableVersion(version1);
        ComparableVersion v2 = new ComparableVersion(version2);
        return v1.compareTo(v2) <= 0;
    }

    /**
     * Returns true if version1 is less than version2.
     */
    public static boolean isLower(String version1, String version2) {
        ComparableVersion v1 = new ComparableVersion(version1);
        ComparableVersion v2 = new ComparableVersion(version2);
        return v1.compareTo(v2) < 0;
    }

    /**
     * Returns true if version1 is greater than version2.
     */
    public static boolean isGreater(String version1, String version2) {
        ComparableVersion v1 = new ComparableVersion(version1);
        ComparableVersion v2 = new ComparableVersion(version2);
        return v1.compareTo(v2) > 0;
    }

    /**
     * Returns true if version1 is equal to version2.
     */
    public static boolean isEqual(String version1, String version2) {
        ComparableVersion v1 = new ComparableVersion(version1);
        ComparableVersion v2 = new ComparableVersion(version2);
        return v1.compareTo(v2) == 0;
    }

    /**
     * Returns true if the version is within the specified range (inclusive).
     * For some cursed version formats this may become exclusive.
     * @param version The version to check
     * @param minVersion The minimum version (inclusive)
     * @param maxVersion The maximum version (inclusive)
     * @return true if the version is within the range
     */
    public static boolean inRange(String version, String minVersion, String maxVersion) {
        ComparableVersion v = new ComparableVersion(version);
        ComparableVersion min = new ComparableVersion(minVersion);
        ComparableVersion max = new ComparableVersion(maxVersion);
        return v.compareTo(min) >= 0 && v.compareTo(max) <= 0;
    }
}
