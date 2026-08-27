package com.alonie.brbe.jei.plugins.loader;

import com.alonie.brbe.BetterRecipeBook;
import mezz.jei.api.IModPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Discovers {@link IModPlugin} instances from every loaded mod, without the
 *  real JEI present.  Fabric exposes plugins through the {@code jei_mod_plugin}
 *  entrypoint, reached reflectively so this module has no platform compile
 *  dependency. */
public final class BrbeJeiPluginFinder {

    private BrbeJeiPluginFinder() {}

    public static List<IModPlugin> findPlugins() {
        List<IModPlugin> plugins = new ArrayList<>();
        tryFindFabric(plugins);
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

}
