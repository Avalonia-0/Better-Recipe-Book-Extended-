package com.alonie.brbe.jei.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge;
import com.alonie.brbe.jei.plugins.engine.PluginRecipeIndexer;
import com.alonie.brbe.jei.plugins.loader.BrbeJeiPluginFinder;
import com.alonie.brbe.jei.plugins.loader.CatalystCollector;
import com.alonie.brbe.jei.plugins.loader.RecipeCategoryCollector;
import com.alonie.brbe.jei.plugins.loader.RecipeCollector;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 1.21.1 编排：发现每个 mod 的 JEI 插件（反射扫描 {@code jei_mod_plugin}
 * entrypoint），把其 registerCategories / registerRecipeCatalysts /
 * registerRecipes 采集进 BRBE 收集器，最后 {@link PluginRecipeIndexer} 索引进
 * 查询引擎——真实 JEI 缺席（headless）时同样工作（mezz.jei.api 内嵌）。
 *
 * <p>独立项目版（headless-jei）：收集结果写 {@code JeiRecipeRegistry} 轻量桥；
 * 消费者（BRBE 主 mod）从桥读取条目转进自己的查询引擎（RecipeViewerEngine），
 * 弹窗渲染经 {@code JeiPopupRenderer}（1.21.1 引擎无 RecipeDisplayEntry——直接用
 * IRecipeManager#createRecipeLayoutDrawable）。
 */
public final class BrbeJeiPlugins {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

    private BrbeJeiPlugins() {}

    /** Called from both platform client-initialization paths (JOIN / level
     *  load).  Idempotent per join; re-runs whenever the caller needs a fresh
     *  collection (registry replace is idempotent). */
    public static void collectAndInject() {
        try {
            // 真实 JEI 存在时同样收集（插件数据喂 BRBE 索引；渲染走真实 JEI）。
            // 注意：JEI 自己的插件（namespace jei）跳过——原版数据已由
            // 消费者侧索引（RecipeViewerIndex）与 vanilla JEI 类型
            // （anvil/grindstone/brewing 等经 VanillaPlugin 注册）覆盖。
            RecipeCategoryCollector categoryCollector = new RecipeCategoryCollector();
            CatalystCollector catalystCollector = new CatalystCollector();
            RecipeCollector recipeCollector = new RecipeCollector();

            List<IModPlugin> plugins = BrbeJeiPluginFinder.findPlugins();
            if (plugins.isEmpty()) {
                LOGGER.info("[BRBE-JEI-Plugins] no JEI plugins found");
                return;
            }
            // 无头核心本身会把 VanillaPlugin/JeiInternalPlugin 装进 JEI 运行时；
            // 原版 anvil/brewing/grindstone 类别与配方数据从运行时直接读取
            // （PluginRecipeIndexer.indexVanillaRuntimeTypes），不在此重跑
            // registerRecipes（其 vanillaRecipeFactory 依赖运行时）。

            for (IModPlugin plugin : plugins) {
                try {
                    Identifier uid = plugin.getPluginUid();
                    if (uid != null && uid.getNamespace().equals("jei")) continue;
                    plugin.registerCategories(categoryCollector);
                    plugin.registerRecipeCatalysts(catalystCollector);
                    plugin.registerRecipes(recipeCollector);
                    LOGGER.info("[BRBE-JEI-Plugins] collected from plugin {}", uid);
                } catch (Exception | LinkageError e) {
                    LOGGER.warn("[BRBE-JEI-Plugins] plugin {} failed: {}",
                            safeUid(plugin), e.toString());
                }
            }

            PluginRecipeIndexer.indexModData(categoryCollector.categories(),
                    recipeCollector.recipes(), catalystCollector.collected());
            // 原版 JEI 类型（anvil/brewing/grindstone）：数据来自 JEI 运行时
            // （嵌入式无头核心或真实 JEI）的 VanillaPlugin 注册。
            PluginRecipeIndexer.indexVanillaRuntimeTypes();
        } catch (Exception e) {
            LOGGER.warn("[BRBE-JEI-Plugins] collection failed: {}", e.toString());
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
