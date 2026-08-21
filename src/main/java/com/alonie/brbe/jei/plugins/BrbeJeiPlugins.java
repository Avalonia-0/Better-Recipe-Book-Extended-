package com.alonie.brbe.jei.plugins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.jei.plugins.engine.PluginRecipeIndexer;
import com.alonie.brbe.jei.plugins.engine.SyntheticRecipeRendererImpl;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.jei.plugins.loader.BrbeJeiPluginFinder;
import com.alonie.brbe.jei.plugins.loader.CatalystCollector;
import com.alonie.brbe.jei.plugins.loader.RecipeCategoryCollector;
import com.alonie.brbe.jei.plugins.loader.RecipeCollector;
import com.alonie.brbe.jei.plugins.loader.WorkstationExporter;
import mezz.jei.api.IModPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Orchestration for the companion mod: load every mod's JEI plugin and funnel
 *  its registered categories, workstation catalysts and recipes into the BRBE
 *  main mod's query engine. */
public final class BrbeJeiPlugins {

    private BrbeJeiPlugins() {}

    /** Re-collect after each recipe-book rebuild (the server's recipe sync), so
     *  mod recipes are indexed once the synchronised recipe registry is full. */
    private static volatile boolean rebuildListenerRegistered = false;

    /** Called from both platform client-initialization paths.  Every mod's JEI
     *  plugin is discovered, its {@code registerCategories} /
     *  {@code registerRecipeCatalysts} / {@code registerRecipes} run against
     *  local collectors, and the result is indexed into the BRBE main mod's
     *  query engine.
     *
     *  <p>The mezz.jei API is provided either by the bundled fork (standalone
     *  jar, no JEI installed) or by the real JEI (jei-compat jar, JEI installed)
     *  — Fabric Loader's {@code depends: jei} selects the right variant, so this
     *  method never needs a runtime JEI check.  JEI's own plugins
     *  ({@code jei:*}) are skipped: their vanilla data is already covered by
     *  BRBE's cache and their vanilla recipe factory is null here. */
    public static void collectAndInject() {
        try {
            if (!rebuildListenerRegistered) {
                rebuildListenerRegistered = true;
                RecipeViewerEngine.registerRebuildListener(BrbeJeiPlugins::collectAndInject);
            }
            List<IModPlugin> plugins = BrbeJeiPluginFinder.findPlugins();
            if (plugins.isEmpty()) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] no JEI plugins found");
                return;
            }

            RecipeCategoryCollector categoryCollector = new RecipeCategoryCollector();
            CatalystCollector catalystCollector = new CatalystCollector();
            RecipeCollector recipeCollector = new RecipeCollector();

            for (IModPlugin plugin : plugins) {
                try {
                    Identifier uid = plugin.getPluginUid();
                    if (uid != null && uid.getNamespace().equals("jei")) continue;
                    plugin.registerCategories(categoryCollector);
                    plugin.registerRecipeCatalysts(catalystCollector);
                    plugin.registerRecipes(recipeCollector);
                    BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] collected from plugin {}", uid);
                } catch (Exception | LinkageError e) {
                    BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] plugin {} failed: {}", safeUid(plugin), e.toString());
                }
            }

            WorkstationExporter.export(catalystCollector.collected());
            // Delegate the full JEI recipe UI to the real JEI runtime.  Without
            // JEI the renderer stays NONE, so callers (hover popups, pin
            // overlay) keep their vanilla-style fallback rendering and its hit
            // geometry.
            if (FabricLoader.getInstance().isModLoaded("jei")) {
                SyntheticRecipeRenderers.register(new SyntheticRecipeRendererImpl());
            }
            PluginRecipeIndexer.indexAll(categoryCollector.categories(),
                    recipeCollector.recipes(), catalystCollector.collected(),
                    categoryCollector.backgrounds());
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] collection failed: {}", e.toString());
        }
    }

    private static String safeUid(IModPlugin plugin) {
        try {
            return String.valueOf(plugin.getPluginUid());
        } catch (Exception | LinkageError e) {
            return "<unknown>";
        }
    }
}
