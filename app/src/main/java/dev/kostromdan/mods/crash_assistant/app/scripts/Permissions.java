package dev.kostromdan.mods.crash_assistant.app.scripts;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.introspection.JexlPermissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Permissions {
    private static JexlEngine engine = null;

    // Stores classes for Context injection (ShortName -> Class).
    // ONLY top-level classes are stored here. Inner classes are accessed via their parents.
    private static final Map<String, Class<?>> CLAZZ_MAP = new HashMap<>();

    // Stores ALL classes (top-level + inner) strictly for JEXL Whitelist.
    private static final Set<String> WHITELISTED_CLASSES = new HashSet<>();

    public static synchronized JexlEngine getEngine() {
        if (engine != null) {
            return engine;
        }

        loadClasses();
        
        // Use SmartJexlPermissions to handle Arrays and Primitives dynamically
        JexlPermissions permissions = new SmartJexlPermissions(WHITELISTED_CLASSES);

        engine = new JexlBuilder()
                .permissions(permissions)
                .strict(true) // If true, throws JexlException when a method or property is not found.
                .silent(false) // If false, exceptions are thrown to the caller instead of being swallowed.
                .safe(false) // x.y() if x is null throws an exception
                .create();

        return engine;
    }

    public static Map<String, Class<?>> getClassMap() {
        if (CLAZZ_MAP.isEmpty()) {
            loadClasses();
        }
        return CLAZZ_MAP;
    }

    private static void loadClasses() {
        CLAZZ_MAP.clear();
        WHITELISTED_CLASSES.clear();

        Map<String, String> registeredShortNames = new HashMap<>();
        String resourcePath = "/jexl_allowed_classes.txt";

        try (InputStream is = Permissions.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("FATAL: Resource not found: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                    try {
                        Class<?> clazz = Class.forName(trimmed);

                        // 1. Always add to whitelist (Permissions)
                        WHITELISTED_CLASSES.add(clazz.getCanonicalName());

                        // 2. Inner classes are NOT registered in the MapContext.
                        // They are accessed via parent (e.g. Map.Entry), so they don't cause short-name collisions.
                        if (clazz.isMemberClass()) {
                            continue;
                        }

                        // 3. Register Top-Level classes with collision check
                        String shortName = clazz.getSimpleName();
                        if (registeredShortNames.containsKey(shortName)) {
                            throw new RuntimeException("FATAL: Simple Name collision: " + trimmed + " vs " + registeredShortNames.get(shortName));
                        }

                        registeredShortNames.put(shortName, trimmed);
                        CLAZZ_MAP.put(shortName, clazz);

                    } catch (ClassNotFoundException ignored) {
                        // Class might be missing in this JRE version, ignore.
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get("app/src/main/resources/jexl_allowed_classes.txt");
        List<String> classes = Files.readAllLines(path, StandardCharsets.UTF_8);

        classes.removeIf(line -> line.trim().isEmpty() || line.trim().startsWith("#"));
        Collections.sort(classes);

        Map<String, String> registeredShortNames = new HashMap<>();

        for (String className : classes) {
            try {
                Class<?> clazz = Class.forName(className);

                // Inner classes are skipped from registration checks
                if (clazz.isMemberClass()) {
                    continue;
                }

                String shortName = clazz.getSimpleName();
                if (registeredShortNames.containsKey(shortName)) {
                    throw new RuntimeException("FATAL: Simple Name collision: " + className + " vs " + registeredShortNames.get(shortName));
                }
                registeredShortNames.put(shortName, className);

            } catch (ClassNotFoundException e) {
                System.out.println("WARNING: Class not found: " + className);
            }
        }

        Files.write(path, classes, StandardCharsets.UTF_8);
    }
}