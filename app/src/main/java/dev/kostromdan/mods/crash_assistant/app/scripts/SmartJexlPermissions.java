package dev.kostromdan.mods.crash_assistant.app.scripts;

import org.apache.commons.jexl3.introspection.JexlPermissions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public class SmartJexlPermissions implements JexlPermissions {
    private final JexlPermissions delegate;

    public SmartJexlPermissions(Set<String> whitelistedClasses) {
        JexlPermissions baseDenyAll = JexlPermissions.parse("dev.kostromdan.mods.crash_assistant.app.scripts.sandbox_allowed.*");
        this.delegate = new JexlPermissions.ClassPermissions(baseDenyAll, whitelistedClasses);
    }

    @Override
    public boolean allow(Class<?> clazz) {
        // As per JexlPermissions advice: overloads should call validate() early.
        if (!delegate.validate(clazz)) {
            return false;
        }

        // 1. Arrays: Standard JexlPermissions blocks arrays not in whitelist.
        // We unwrap them to check component type.
        if (clazz.isArray()) {
            return allow(clazz.getComponentType());
        }

        // 2. Primitives:
        // We permit all standard primitives (int, boolean, etc.) as their wrappers are whitelisted.
        if (clazz.isPrimitive()) {
            return true;
        }

        // 3. Standard check: checks whitelist and base permissions.
        return delegate.allow(clazz);
    }



    // Delegate other methods
    @Override
    public boolean allow(Package pack) {
        return delegate.allow(pack);
    }

    @Override
    public boolean allow(Method method) {
        return delegate.allow(method);
    }

    @Override
    public boolean allow(Field field) {
        return delegate.allow(field);
    }

    @Override
    public boolean allow(Constructor<?> ctor) {
        return delegate.allow(ctor);
    }

    @Override
    public boolean validate(Class<?> clazz) {
        return delegate.validate(clazz);
    }

    @Override
    public boolean validate(Package pack) {
        return delegate.validate(pack);
    }

    @Override
    public boolean validate(Method method) {
        return delegate.validate(method);
    }

    @Override
    public boolean validate(Constructor<?> ctor) {
        return delegate.validate(ctor);
    }

    @Override
    public boolean validate(Field field) {
        return delegate.validate(field);
    }

    @Override
    public JexlPermissions compose(String... src) {
        // Warning: This returns the delegate's composition (losing Smart logic for the new instance).
        // Since we don't expect dynamic updates in this usage, it might be acceptable for now.
        return delegate.compose(src);
    }
}
