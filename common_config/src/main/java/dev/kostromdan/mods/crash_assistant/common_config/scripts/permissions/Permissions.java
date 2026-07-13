package dev.kostromdan.mods.crash_assistant.common_config.scripts.permissions;

import dev.kostromdan.mods.crash_assistant.common_config.scripts.JexlStreamArithmetic;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.introspection.JexlPermissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
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

    // Packages whose non-public runtime implementations must remain visible to JEXL.
    private static final Set<String> WHITELISTED_PACKAGES = new HashSet<>();

    private static final boolean isDevEnvironment = Files.isRegularFile(Paths.get("app/src/main/java/dev/kostromdan/mods/crash_assistant/app/CrashAssistantApp.java"));


    public static synchronized JexlEngine getEngine() {
        if (engine != null) {
            return engine;
        }

        loadClasses();

        JexlPermissions permissions = JexlPermissions.NONE.compose(createPermissionsText());

        engine = new JexlBuilder()
                .permissions(permissions)
                .features(JexlFeatures.createDefault())
                .arithmetic(new JexlStreamArithmetic(true))
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
        WHITELISTED_PACKAGES.clear();

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
                    if (trimmed.endsWith(".*")) {
                        WHITELISTED_PACKAGES.add(trimmed.substring(0, trimmed.length() - 2));
                        continue;
                    }
                    if (isRelocatedCommonsPackageInDevEnv(trimmed)) {
                        // Revert relocating package. In dev runs classes are not relocated yet.
                        trimmed = trimmed.replace("crash_assistant_relocated_libs.", "org.apache.commons.");
                    }

                    try {
                        Class<?> clazz = Class.forName(trimmed);

                        // 1. Always add the class and its runtime implementation classes to the whitelist.
                        addWhitelistedClass(clazz);

                        // 2. Inner classes are NOT registered in the MapContext.
                        // They are accessed via parent (e.g. Map.Entry), so they don't cause short-name collisions.
                        if (clazz.getEnclosingClass() != null || !Modifier.isPublic(clazz.getModifiers())) {
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

    private static void addWhitelistedClass(Class<?> clazz) {
        String permissionClassName = clazz.isAnonymousClass()
                ? clazz.getEnclosingClass().getName() + "$"
                : clazz.getName();
        if (!WHITELISTED_CLASSES.add(permissionClassName)) {
            return;
        }
        String className = clazz.getName();
        boolean isCollectionType = java.util.Collection.class.isAssignableFrom(clazz)
                || java.util.Map.class.isAssignableFrom(clazz);
        boolean isCollectionFactory = className.equals("java.util.Arrays")
                || className.equals("java.util.Comparators")
                || className.equals("java.util.Collections")
                || className.equals("java.util.ImmutableCollections")
                || className.equals("java.util.Spliterators");
        if (!className.startsWith("java.util.") || (!isCollectionType && !isCollectionFactory)) {
            return;
        }
        for (Class<?> nestedClass : clazz.getDeclaredClasses()) {
            addWhitelistedClass(nestedClass);
        }
    }

    private static String createPermissionsText() {
        Map<String, Set<String>> classesByPackage = new HashMap<>();
        for (String className : WHITELISTED_CLASSES) {
            int packageEnd = className.lastIndexOf('.');
            classesByPackage
                    .computeIfAbsent(className.substring(0, packageEnd), ignored -> new HashSet<>())
                    .add(className.substring(packageEnd + 1));
        }

        StringBuilder rules = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : classesByPackage.entrySet()) {
            rules.append(entry.getKey()).append(" {");
            for (String className : entry.getValue()) {
                rules.append(" +").append(className).append("{}");
            }
            if ("java.lang".equals(entry.getKey())) {
                rules.append(" +Class { getName(); getSimpleName(); }");
            }
            rules.append(" }\n");
        }
        for (String packageName : WHITELISTED_PACKAGES) {
            rules.append(packageName).append(" +{}\n");
        }
        return rules.toString();
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get("common_config/src/main/resources/jexl_allowed_classes.txt");
        List<String> classes = Files.readAllLines(path, StandardCharsets.UTF_8);

        classes.removeIf(line -> line.trim().isEmpty() || line.trim().startsWith("#"));
        Collections.sort(classes);

        Map<String, String> registeredShortNames = new HashMap<>();

        for (String className : classes) {
            if (className.endsWith(".*")) {
                continue;
            }
            if (isRelocatedCommonsPackageInDevEnv(className)) {
                // Revert relocating package. In dev runs classes are not relocated yet.
                className = className.replace("crash_assistant_relocated_libs.", "org.apache.commons.");
            }
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

    public static boolean isRelocatedCommonsPackageInDevEnv(String className) {
        return isDevEnvironment && (className.startsWith("crash_assistant_relocated_libs.logging.") || className.startsWith("crash_assistant_relocated_libs.jexl3."));
    }

    public static boolean isDevEnvironment() {
        return isDevEnvironment;
    }
}
