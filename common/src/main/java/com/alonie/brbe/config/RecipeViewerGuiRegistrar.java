package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 查询功能布尔配置项的统一渲染（"查询合成/用途" 标题行 + 开关），由 JVM 盾门控。
 *
 * <p>{@code recipeViewerEnabled}（总开关）原带 {@code @ConfigEntry.Gui.PrefixText}
 * "查询合成/用途"，本类接管其渲染（标题行 + 布尔开关），避免依赖 AutoConfig
 * 注解渲染的次序——屏蔽时返回空列表即连同标题行一起隐藏。同时接管
 * {@code queryScope}（仅查询 viewer 的过滤项；2026-10-03 起是**枚举**，
 * 本类改用同一套 {@code startSelector} 选择器渲染，见 {@code QUERY_SCOPE_FIELD}）。</p>
 *
 * <p>屏蔽（{@code brbe.disableRecipeViewer=true}，默认）时两个字段连同标题行
 * 全部隐藏；未屏蔽时原样渲染（标题行 + 开关，视觉与移除注解前一致）。
 * R/U 键位项另由 {@link KeybindingGuiRegistrar} 隐藏；pin key 与配方书相关
 * 配置不受影响。配置值始终保留在配置里，仅 UI 隐藏/显示。</p>
 */
public final class RecipeViewerGuiRegistrar {

    /** 查询 viewer 专属配置字段（本类接管渲染：布尔项 + 枚举项「查询范围」）。 */
    private static final Set<String> VIEWER_FIELDS =
            Set.of("recipeViewerEnabled", "queryScope");

    /** 枚举项「查询范围」的字段名（单独走选择器渲染）。 */
    private static final String QUERY_SCOPE_FIELD = "queryScope";

    /** 「查询范围」的 tooltip 行数 —— 与 {@code BrbeConfig} 上 {@code @Tooltip(count = N)} 的 N 一致。 */
    private static final int QUERY_SCOPE_TOOLTIP_LINES = 2;

    /** 带 "查询合成/用途" 标题行（原 @ConfigEntry.Gui.PrefixText）的字段。 */
    private static final Set<String> HEADING_FIELDS =
            Set.of("recipeViewerEnabled");

    private RecipeViewerGuiRegistrar() {
    }

    public static void register() {
        try {
            GuiRegistry registry = AutoConfig.getGuiRegistry(BrbeConfig.class);
            registry.registerPredicateProvider((i18n, field, config, defaults, registryAccess) -> {
                BrbeConfig brbe = (BrbeConfig) config;
                boolean hidden = RecipeViewerFeatureFlag.isDisabled();
                // 屏蔽：返回空列表（连标题行一起隐藏）。
                if (hidden) {
                    return List.of();
                }
                List<AbstractConfigListEntry> out = new ArrayList<>();
                ConfigEntryBuilder builder = ConfigEntryBuilder.create();
                // 标题行（原 @PrefixText "查询合成/用途"），仅带标题的字段渲染。
                if (HEADING_FIELDS.contains(field.getName())
                        && I18n.exists("text.autoconfig.brbe.option."
                                + field.getName() + ".@PrefixText")) {
                    out.add(builder.startTextDescription(Component.translatable(
                                    "text.autoconfig.brbe.option." + field.getName() + ".@PrefixText"))
                            .build());
                }
                // 「查询范围」是**枚举**项：本类是 predicate provider，会盖掉 AutoConfig 内置的
                // 枚举 provider（含 2026-10-03（十）发现的 @EnumHandler(BUTTON) 切换按钮路径），
                // 所以这里自己造同款选择器；仍受上面的"屏蔽时整段隐藏"门控。
                if (QUERY_SCOPE_FIELD.equals(field.getName())) {
                    BrbeConfig.QueryScope current = brbe.queryScope == null
                            ? BrbeConfig.QueryScope.ALL_CATEGORIES : brbe.queryScope;
                    var selector = builder.startSelector(
                                    Component.translatable("text.autoconfig.brbe.option." + field.getName()),
                                    BrbeConfig.QueryScope.values(), current)
                            .setDefaultValue(BrbeConfig.QueryScope.ALL_CATEGORIES)
                            .setNameProvider(mode -> Component.translatable(mode.getKey()))
                            .setSaveConsumer(mode -> {
                                if (mode != null) brbe.queryScope = mode;
                            });
                    // tooltip 与「标签模式」同排版：多行由 …@Tooltip[0] / [1] 拼（Cloth 的
                    // @Tooltip(count = N) 机制），本类自己造条目，所以手动按行取键。
                    Component[] tooltip = new Component[QUERY_SCOPE_TOOLTIP_LINES];
                    for (int i = 0; i < tooltip.length; i++) {
                        tooltip[i] = Component.translatable(
                                "text.autoconfig.brbe.option." + field.getName() + ".@Tooltip[" + i + "]");
                    }
                    selector.setTooltip(tooltip);
                    out.add(selector.build());
                    return out;
                }
                // 布尔开关。
                var toggle = builder.startBooleanToggle(
                                Component.translatable("text.autoconfig.brbe.option." + field.getName()),
                                readBoolean(field, brbe, true))
                        .setDefaultValue(readBoolean(field, brbe, true))
                        .setSaveConsumer(value -> writeBoolean(field, brbe, value));
                if (I18n.exists("text.autoconfig.brbe.option."
                        + field.getName() + ".@Tooltip")) {
                    toggle.setTooltip(Component.translatable(
                            "text.autoconfig.brbe.option." + field.getName() + ".@Tooltip"));
                }
                out.add(toggle.build());
                return out;
            }, field -> VIEWER_FIELDS.contains(field.getName()));
        } catch (Throwable t) {
            // Cloth Config absent — GUI-only feature, skip.
            BetterRecipeBook.LOGGER.warn("[BRBE] Recipe viewer GUI registration skipped: {}", t.toString());
        }
    }

    private static boolean readBoolean(Field field, BrbeConfig brbe, boolean fallback) {
        try {
            field.setAccessible(true);
            return field.getBoolean(brbe);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void writeBoolean(Field field, BrbeConfig brbe, boolean value) {
        try {
            field.setAccessible(true);
            field.setBoolean(brbe, value);
        } catch (Exception e) {
            // broken field write — ignore
        }
    }
}
