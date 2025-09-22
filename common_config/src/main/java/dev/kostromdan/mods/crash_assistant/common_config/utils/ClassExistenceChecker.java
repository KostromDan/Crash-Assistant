package dev.kostromdan.mods.crash_assistant.common_config.utils;

/**
 * Utility interface for checking if a class exists.
 */
public interface ClassExistenceChecker {
    /**
     * Checks if a class with the given name exists.
     *
     * @param className the fully qualified name of the class to check
     * @return true if the class exists, false otherwise
     */
    static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}