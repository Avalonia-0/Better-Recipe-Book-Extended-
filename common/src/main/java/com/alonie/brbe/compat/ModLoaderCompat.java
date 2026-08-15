package com.alonie.brbe.compat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mod introspection via reflection — zero compile-time dependency on Fabric
 * Loader.  The loader is probed at runtime through the FabricLoader API.
 */
public final class ModLoaderCompat {

    private ModLoaderCompat() {}

    /** Whether {@code modId} is present on Fabric. */
    public static boolean isModLoaded(String modId) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = loaderClass.getMethod("getInstance").invoke(null);
            return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                    .invoke(instance, modId);
        } catch (Throwable ignored) { }
        return false;
    }

    /**
     * The on-disk paths of every loaded mod's file (jar or folder) on Fabric.
     * Used to scan {@code data/<namespace>/recipe/*.json} locally.
     */
    public static List<Path> getModJarPaths() {
        List<Path> result = new ArrayList<>();

        // Fabric: FabricLoader.getAllMods() → ModContainer.getOrigin().getPaths()
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = loaderClass.getMethod("getInstance").invoke(null);
            Object mods = loaderClass.getMethod("getAllMods").invoke(instance);
            for (Object mod : (Iterable<?>) mods) {
                Object origin = mod.getClass().getMethod("getOrigin").invoke(mod);
                if (origin == null) continue;
                try {
                    Object paths = origin.getClass().getMethod("getPaths").invoke(origin);
                    for (Object p : (Iterable<?>) paths) {
                        if (p instanceof Path path) result.add(path);
                    }
                } catch (NoSuchMethodException ignored) { }
            }
        } catch (Throwable ignored) { }

        return result;
    }
}
