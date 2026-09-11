package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.ConfigTipCarousel;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面轮循提示行注册表 + 统一打开入口。
 *
 * <p>通过 {@link #registerCarousel} 注册 {@link ConfigTipCarousel}（每个绑定一个配置分类
 * 页面 + 文案池）。打开配置界面时遍历所有注册的轮循行，在各自分类最上方插入显示行。
 * 每次打开每个轮循行随机选一条且避免相邻重复。</p>
 *
 * <p>所有配置界面入口统一走 {@link #openConfigScreen}（自动带轮循行）。</p>
 */
public final class ConfigTipsHelper {

    private static final List<ConfigTipCarousel> CAROUSELS = new ArrayList<>();

    static {
        // 默认：实用功能页面顶部"提示：xxx"功能 tips 轮循行
        registerCarousel(ConfigTipCarousel.builder()
                .category(Component.translatable("text.autoconfig.brbe.category.default"))
                .prefix(Component.translatable("brbe.gui.tip.prefix"))
                .tipKeys(List.of("brbe.gui.tip.1", "brbe.gui.tip.2", "brbe.gui.tip.3", "brbe.gui.tip.4", "brbe.gui.tip.5", "brbe.gui.tip.6", "brbe.gui.tip.7", "brbe.gui.tip.8", "brbe.gui.tip.9"))
                .build());
    }

    private ConfigTipsHelper() {
    }

    /**
     * 注册一个配置界面轮循提示行。
     */
    public static void registerCarousel(ConfigTipCarousel carousel) {
        CAROUSELS.add(carousel);
    }

    /**
     * 打开配置界面（注入所有注册的轮循行）。所有入口统一走这里。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void openConfigScreen(Class configClass, Screen parent) {
        try {
            var provider = (ConfigScreenProvider) AutoConfig.getConfigScreen(configClass, parent);
            java.util.function.Function<ConfigBuilder, Screen> buildFn = builder -> {
                addCarousels(builder);
                addSectionLabels(builder);
                return builder.build();
            };
            provider.setBuildFunction(buildFn);
            Minecraft.getInstance().setScreen(provider.get());
        } catch (NoClassDefFoundError e) {
            // Cloth Config not available
        }
    }

    /** 「快捷键&数值」页底部的分节标题行：黄色纯文字「音效与动画」，插在数值项之前。
     *  与轮循 tips 无关，故不受 {@code hideConfigTips} 影响。 */
    private static final String SECTION_CATEGORY_KEY = "text.autoconfig.brbe.category.keybindings";
    private static final String SECTION_LABEL_KEY = "brbe.gui.section.soundAnimation";
    /** 分节标题的锚点：插在这一项之前（识别按 AutoConfig 的 option i18n 键，与语言无关）。 */
    private static final String SECTION_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.pageFlipVolume";

    /**
     * 往「快捷键&数值」类别里插一条纯文字分节标题行（黄色）。
     *
     * <p>Cloth 的类别条目由 AutoConfig 按字段声明顺序生成，标题行只能在这里插进条目列表：
     * 定位方式是把条目的 {@code getFieldName()} 与 {@code text.autoconfig.brbe.option.pageFlipVolume}
     * 的翻译组件比较（AutoConfig 的字段条目名就是 {@code Component.translatable(optionI13n)}），
     * 找到就插在它前面，找不到（例如将来该项被移除）则退化为追加到该类别末尾。</p>
     */
    private static void addSectionLabels(ConfigBuilder builder) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable(SECTION_CATEGORY_KEY));
        List<Object> entries = category.getEntries();
        Component anchor = Component.translatable(SECTION_ANCHOR_OPTION_KEY);
        int at = entries.size();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof AbstractConfigListEntry<?> entry && anchor.equals(entry.getFieldName())) {
                at = i;
                break;
            }
        }
        entries.add(at, builder.entryBuilder()
                .startTextDescription(Component.translatable(SECTION_LABEL_KEY).withStyle(ChatFormatting.YELLOW))
                .build());
    }

    private static void addCarousels(ConfigBuilder builder) {
        if (BetterRecipeBook.config.hideConfigTips) return;
        for (ConfigTipCarousel carousel : CAROUSELS) {
            if (!carousel.hasTips()) continue;
            ConfigCategory category = builder.getOrCreateCategory(carousel.categoryTitle());
            int idx = carousel.nextTipIndex();
            Component line = carousel.prefix() == null
                    ? carousel.tipAt(idx).copy().withStyle(carousel.style())
                    : carousel.prefix().copy().withStyle(carousel.style())
                            .append(carousel.tipAt(idx).copy().withStyle(carousel.style()));
            category.getEntries().add(0, builder.entryBuilder().startTextDescription(line).build());
        }
    }
}
