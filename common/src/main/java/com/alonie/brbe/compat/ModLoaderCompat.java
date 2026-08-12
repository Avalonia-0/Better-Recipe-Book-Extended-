package com.alonie.brbe.compat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-loader mod introspection via reflection — zero compile-time dependency
 * on any loader API.  Both Fabric Loader and NeoForge ModList are probed at
 * runtime; the first loader that answers wins per call.
 */
public final class ModLoaderCompat {

    private ModLoaderCompat() {}

    /** Whether {@code modId} is present on either loader. */
    public static boolean isModLoaded(String modId) {
        // Fabric Loader
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = loaderClass.getMethod("getInstance").invoke(null);
            return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                    .invoke(instance, modId);
        } catch (Throwable ignored) { }

        // NeoForge ModList
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object instance = modListClass.getMethod("get").invoke(null);
            return (boolean) instance.getClass().getMethod("isLoaded", String.class)
                    .invoke(instance, modId);
        } catch (Throwable ignored) { }

        // Legacy FMLLoader (older NeoForge / Forge)
        try {
            Class<?> fmlClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
            return (boolean) fmlClass.getMethod("isModLoaded", String.class).invoke(null, modId);
        } catch (Throwable ignored) { }

        return false;
    }

    /**
     * The on-disk paths of every loaded mod's file (jar or folder), from both
     * loaders.  Used to scan {@code data/<namespace>/recipe/*.json} locally.
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

        // NeoForge: ModList.getMods() → IModInfo.getOwningFile().getFile().getFilePath()
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            Object mods = modListClass.getMethod("getMods").invoke(modList);
            for (Object info : (Iterable<?>) mods) {
                Method owningFile = info.getClass().getMethod("getOwningFile");
                Object fileInfo = owningFile.invoke(info);
                if (fileInfo == null) continue;
                Object file = fileInfo.getClass().getMethod("getFile").invoke(fileInfo);
                if (file == null) continue;
                Object path = file.getClass().getMethod("getFilePath").invoke(file);
                if (path instanceof Path p) result.add(p);
            }
        } catch (Throwable ignored) { }

        return result;
    }
}
