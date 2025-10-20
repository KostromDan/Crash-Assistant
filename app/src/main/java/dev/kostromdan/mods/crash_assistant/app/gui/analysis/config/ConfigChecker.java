package dev.kostromdan.mods.crash_assistant.app.gui.analysis.config;

import java.nio.file.Path;
import java.util.Set;

/**
 * Performs validation for configuration files of specific extensions.
 * Implementations must throw an exception when validation fails.
 */
public interface ConfigChecker {

    /**
     * @return lowercase extensions supported by this checker (without dots).
     */
    Set<String> supportedExtensions();

    /**
     * @return human-readable checker name for logging/debugging.
     */
    String getName();

    /**
     * Validates the provided configuration file. Implementations must throw an exception when
     * the file is invalid or unreadable. A successful return indicates the file is valid.
     *
     * @param path absolute or relative path to the configuration file
     * @throws Exception when validation fails
     */
    void check(Path path) throws Exception;
}
