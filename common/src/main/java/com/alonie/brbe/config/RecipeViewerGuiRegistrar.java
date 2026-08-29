package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;

import java.util.List;
import java.util.Set;

/**
 * JVM 盾生效时从 Cloth Config GUI 隐藏查询功能的布尔配置项。
 *
 * <p>当 {@code brbe.disableRecipeViewer=true}（默认）时，查询 viewer 的整体
 * 屏蔽落地：从配置界面隐藏 {@code recipeViewerEnabled}（总开关）与
 * {@code hideNoRecipeBookStationObjects}（仅查询 viewer 的过滤项）两个
 * 布尔配置。R/U 键位项另由 {@link KeybindingGuiRegistrar} 隐藏；pin key 与
 * 配方书相关配置不受影响。</p>
 *
 * <p>只在屏蔽时注册 predicate provider（AutoConfig 的 predicate provider
 * 会替换该字段的默认渲染，如 {@link PinyinSearchGuiRegistrar} 对
 * {@code pinyinSearch} 那样）——未屏蔽时不注册，这两个字段沿用 AutoConfig
 * 默认渲染（保留 {@code @ConfigEntry.Gui.PrefixText} 等原样）。配置值始终
 * 保留，仅 UI 隐藏/显示；屏蔽时返回空列表即隐藏。</p>
 */
public final class RecipeViewerGuiRegistrar {

    /** 查询 viewer 专属布尔配置字段（屏蔽时隐藏）。 */
    private static final Set<String> VIEWER_BOOLEAN_FIELDS =
            Set.of("recipeViewerEnabled", "hideNoRecipeBookStationObjects");

    private RecipeViewerGuiRegistrar() {
    }

    public static void register() {
        try {
            // 未屏蔽：不覆盖默认渲染（保留 @PrefixText 等），直接返回。
            if (!RecipeViewerFeatureFlag.isDisabled()) {
                return;
            }
            GuiRegistry registry = AutoConfig.getGuiRegistry(BrbeConfig.class);
            registry.registerPredicateProvider(
                    (i18n, field, config, defaults, registryAccess) -> List.of(),
                    field -> VIEWER_BOOLEAN_FIELDS.contains(field.getName()));
        } catch (Throwable t) {
            // Cloth Config absent — GUI-only feature, skip.
            BetterRecipeBook.LOGGER.warn("[BRBE] Recipe viewer GUI registration skipped: {}", t.toString());
        }
    }
}
