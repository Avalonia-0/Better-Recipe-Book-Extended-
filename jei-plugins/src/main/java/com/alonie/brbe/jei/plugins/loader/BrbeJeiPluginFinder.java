package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.BetterRecipeBook;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Discovers {@link IModPlugin} instances from every loaded mod, without the
 *  real JEI present.  Fabric exposes plugins through the {@code jei_mod_plugin}
 *  entrypoint; NeoForge through {@link JeiPlugin}-annotated classes scanned from
 *  mod jars.  Both are reached reflectively so this module has no platform
 *  compile dependency (single jar, dual loader). */
public final class BrbeJeiPluginFinder {

    private BrbeJeiPluginFinder() {}

    public static List<IModPlugin> findPlugins() {
        List<IModPlugin> plugins = new ArrayList<>();
        tryFindFabric(plugins);
        tryFindNeoForge(plugins);
        return plugins;
    }

    private static void tryFindFabric(List<IModPlugin> out) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Method getContainers = loaderClass.getMethod("getEntrypointContainers", String.class, Class.class);
            Object containers = getContainers.invoke(loader, "jei_mod_plugin", IModPlugin.class);
            if (!(containers instanceof Iterable<?> iterable)) return;
            for (Object container : iterable) {
                try {
                    Object entrypoint = container.getClass().getMethod("getEntrypoint").invoke(container);
                    if (entrypoint instanceof IModPlugin plugin) out.add(plugin);
                } catch (ReflectiveOperationException | LinkageError e) {
                    BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] broken fabric plugin container: {}", e.toString());
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            // not on Fabric
        }
    }

    private static void tryFindNeoForge(List<IModPlugin> out) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            Object scanDataList = modListClass.getMethod("getAllScanData").invoke(modList);
            if (!(scanDataList instanceof Iterable<?> scanDataIterable)) return;
            Class<?> asmType = Class.forName("org.objectweb.asm.Type");
            Method asmGetType = asmType.getMethod("getType", Class.class);
            Object jeiPluginType = asmGetType.invoke(null, JeiPlugin.class);
            for (Object scanData : scanDataIterable) {
                Object annotations;
                try {
                    annotations = scanData.getClass().getMethod("getAnnotations").invoke(scanData);
                } catch (NoSuchMethodException e) {
                    continue;
                }
                if (!(annotations instanceof Iterable<?> annotationIterable)) continue;
                for (Object annotation : annotationIterable) {
                    try {
                        Object annotationType = annotation.getClass().getMethod("annotationType").invoke(annotation);
                        if (!jeiPluginType.equals(annotationType)) continue;
                        String className = (String) annotation.getClass().getMethod("memberName").invoke(annotation);
                        Class<?> cls = Class.forName(className);
                        Class<? extends IModPlugin> pluginClass = cls.asSubclass(IModPlugin.class);
                        out.add(pluginClass.getDeclaredConstructor().newInstance());
                    } catch (ReflectiveOperationException | LinkageError e) {
                        BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] broken neoforge plugin: {}", e.toString());
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            // not on NeoForge
        }
    }
}
