package dev.kostromdan.mods.crash_assistant.app.utils.maven_version_cmp;

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
}
