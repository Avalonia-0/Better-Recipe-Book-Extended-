package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.WorldScopedStore;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 「标签模式」（{@code [rbip] tabMode}）那一行的 GUI：**联机时隐藏「数据包」档**。
 *
 * <p>数据包档要读服务端的资源包列表（{@code RecipePackIndex}），而网络协议里没有这条信息 ——
 * 联机时它等于"一个「服务器」标签"，不如让玩家看到正常的命名空间分类。用户 2026-10-01 定：
 * 联机时该档**自动按命名空间档跑**、选项里也**看不到它**，离开服务器恢复（存储值不动）。</p>
 *
 * <h3>为什么用 transformer 而不是 provider</h3>
 * <p>AutoConfig 内置的枚举 provider（{@code @ConfigEntry.Gui.EnumHandler}）在
 * {@code GuiRegistry} 的同一个优先级桶里、注册时间更早，而 {@code get()} 用的是
 * {@code findFirst()} —— 后注册的 predicate provider 抢不到这个字段。transformer 不同：
 * {@code transform()} 会把**所有**匹配的 transformer 依次跑一遍，所以由它把已生成的枚举条目
 * <b>换掉</b>最稳（BRBE 的 {@code ConfigTipsHelper} 也是靠 transformer 搬条目的）。</p>
 */
public final class TabModeGuiRegistrar {

    private static final String FIELD_NAME = "tabMode";
    /** tooltip 行数 —— 必须与 {@code BrbeConfig} 上 {@code @Tooltip(count = N)} 的 N 一致。 */
    private static final int TOOLTIP_LINES = 3;

    private TabModeGuiRegistrar() {
    }

    public static void register() {
        try {
            GuiRegistry registry = AutoConfigClient.getGuiRegistry(BrbeConfig.class);
            registry.registerPredicateTransformer(TabModeGuiRegistrar::replaceEntry,
                    field -> FIELD_NAME.equals(field.getName()));
        } catch (Throwable t) {
            // Cloth Config absent, or the registry could not be reached — GUI-only feature, skip.
            BetterRecipeBook.LOGGER.warn("[BRBE] Tab mode GUI registration skipped: {}", t.toString());
        }
    }

    /** 把 AutoConfig 生成的枚举条目换成我们自己的选择器（可选档位随联机状态收窄）。 */
    private static List<AbstractConfigListEntry> replaceEntry(List<AbstractConfigListEntry> entries,
                                                              String i18n, Field field, Object config,
                                                              Object defaults,
                                                              me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess access) {
        if (!FIELD_NAME.equals(field.getName())) return entries;
        if (BetterRecipeBook.config == null) return entries;
        try {
            BrbeConfig.RecipeBookIsPain rbip = BetterRecipeBook.config.rbip;
            List<BrbeConfig.RecipeBookIsPain.TabMode> visible = visibleModes();
            BrbeConfig.RecipeBookIsPain.TabMode current = currentValue(rbip, visible);

            Component[] tooltip = new Component[TOOLTIP_LINES];
            for (int i = 0; i < TOOLTIP_LINES; i++) {
                tooltip[i] = Component.translatable(i18n + ".@Tooltip[" + i + "]");
            }

            var entry = ConfigEntryBuilder.create()
                    .startSelector(Component.translatable(i18n),
                            visible.toArray(new BrbeConfig.RecipeBookIsPain.TabMode[0]), current)
                    .setDefaultValue(BrbeConfig.RecipeBookIsPain.TabMode.CREATIVE_TABS)
                    .setNameProvider(TabModeGuiRegistrar::displayName)
                    .setTooltip(tooltip)
                    .setSaveConsumer(mode -> save(rbip, mode))
                    .build();

            List<AbstractConfigListEntry> replaced = new ArrayList<>(1);
            replaced.add(entry);
            return replaced;
        } catch (Throwable t) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Tab mode entry rebuild failed: {}", t.toString());
            return entries;
        }
    }

    /** 联机（真正的服务器）时「数据包」档不可选 —— 它在那里拿不到服务端包列表。 */
    private static List<BrbeConfig.RecipeBookIsPain.TabMode> visibleModes() {
        List<BrbeConfig.RecipeBookIsPain.TabMode> out = new ArrayList<>(3);
        out.add(BrbeConfig.RecipeBookIsPain.TabMode.CREATIVE_TABS);
        out.add(BrbeConfig.RecipeBookIsPain.TabMode.NAMESPACE);
        if (!WorldScopedStore.onRemoteServer()) {
            out.add(BrbeConfig.RecipeBookIsPain.TabMode.DATAPACK);
        }
        return out;
    }

    /**
     * 选择器上显示的当前值：**存储值是数据包档但正联机**时显示命名空间档（也就是实际在跑的
     * 那一档），让"看到的"与"生效的"一致 —— 存储值保持不动，离开服务器就恢复。
     */
    private static BrbeConfig.RecipeBookIsPain.TabMode currentValue(BrbeConfig.RecipeBookIsPain rbip,
                                                                    List<BrbeConfig.RecipeBookIsPain.TabMode> visible) {
        BrbeConfig.RecipeBookIsPain.TabMode effective = rbip.effectiveTabMode();
        if (visible.contains(effective)) return effective;
        return visible.get(visible.size() - 1);
    }

    /**
     * 写回配置：正联机、存储值仍是数据包档、而玩家选的还是命名空间档时**什么都不做** ——
     * 那只是"降级显示"被确认了一次，不该把玩家的数据包档偏好抹掉（用户要求离开服务器恢复）。
     */
    private static void save(BrbeConfig.RecipeBookIsPain rbip, BrbeConfig.RecipeBookIsPain.TabMode mode) {
        if (mode == null) return;
        if (mode == BrbeConfig.RecipeBookIsPain.TabMode.NAMESPACE
                && rbip.datapackModeHidden()) {
            return;
        }
        rbip.tabMode = mode;
    }

    private static Component displayName(BrbeConfig.RecipeBookIsPain.TabMode mode) {
        return Component.translatable(mode.getKey());
    }
}
