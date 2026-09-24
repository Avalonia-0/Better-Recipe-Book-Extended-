package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 拼音搜索配置项的条件显示。
 *
 * <p>仅中文语言（zh_*）下在配置界面显示该选项（默认开启，可手动关闭）；
 * 其他语言下隐藏（启动时被强制关闭）。GUI 构建时按当前语言实时决定显示与否。</p>
 *
 * <p>语言判定与「语言相关默认值」收口在 {@link PinyinSearchDefaults}——启动钩子与
 * {@code /brbe clear configchange} 的恢复默认共用那一份语义，本类只负责"显示与否"。</p>
 */
public final class PinyinSearchGuiRegistrar {

    private PinyinSearchGuiRegistrar() {
    }

    public static void register() {
        try {
            GuiRegistry registry = AutoConfig.getGuiRegistry(BrbeConfig.class);
            registry.registerPredicateProvider((i18n, field, config, defaults, registryAccess) -> {
                if (!PinyinSearchDefaults.isChineseLanguage()) {
                    // 非中文语言：隐藏配置项
                    return List.of();
                }
                BrbeConfig brbe = (BrbeConfig) config;
                return List.of(ConfigEntryBuilder.create()
                        .startBooleanToggle(
                                Component.translatable("text.autoconfig.brbe.option.pinyinSearch"),
                                brbe.pinyinSearch)
                        .setDefaultValue(true)
                        .setTooltip(Component.translatable("text.autoconfig.brbe.option.pinyinSearch.@Tooltip"))
                        .setSaveConsumer(value -> brbe.pinyinSearch = value)
                        .build());
            }, field -> field.getName().equals("pinyinSearch"));
        } catch (Throwable t) {
            // Cloth Config absent — GUI-only feature, skip.
            BetterRecipeBook.LOGGER.warn("[BRBE] Pinyin search GUI registration skipped: {}", t.toString());
        }
    }
}
