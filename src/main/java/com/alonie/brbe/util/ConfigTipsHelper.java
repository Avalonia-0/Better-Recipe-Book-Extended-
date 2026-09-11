package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.ConfigTipCarousel;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.gui.entries.TextListEntry;
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
                relocateRbipEntries(builder);
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
        int at = indexOfFieldName(entries, Component.translatable(SECTION_ANCHOR_OPTION_KEY));
        entries.add(at < 0 ? entries.size() : at, textRow(builder, SECTION_LABEL_KEY));
    }

    // ── 「界面」页的「Recipe Book Is Pain」小节（条目搬运）──────────────────────

    /** RBIP 的两个子开关（在 {@code [rbip]} 子对象里）要在「界面」页显示的分区锚点。 */
    private static final String UI_CATEGORY_KEY = "text.autoconfig.brbe.category.ui";
    private static final String DEFAULT_CATEGORY_KEY = "text.autoconfig.brbe.category.default";
    private static final String UI_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.hideReiJeiOverlay";
    private static final String RBIP_SECTION_LABEL_KEY = "brbe.gui.section.recipeBookIsPain";
    /** 依次搬过去的条目（相对顺序即此表顺序）。 */
    private static final List<String> RBIP_MOVED_OPTION_KEYS = List.of(
            "text.autoconfig.brbe.option.rbip.enableTabPage",
            "text.autoconfig.brbe.option.rbip.hideTabPageButtons");
    /** RBIP 主开关：移到「§eJust Emulated Items」文字行（{@code recipeViewerEnabled} 项）之前。 */
    private static final String RBIP_MASTER_OPTION_KEY = "text.autoconfig.brbe.option.rbip.enableRecipeBookIsPain";
    private static final String VIEWER_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.recipeViewerEnabled";

    /**
     * 重排 RBIP 相关的 GUI 条目（只重排条目对象，字段与 TOML 路径都保持原样）：
     * <ol>
     *   <li>主开关「启用RBIP」从 {@code rbip} 字段所在位置（「预览模式」之后）移到
     *       「§eJust Emulated Items」文字行之前 —— 即 {@code recipeViewerEnabled} 项之前；</li>
     *   <li>「启用上侧和下侧的标签」「隐藏翻页按钮」从「实用功能」页搬到「界面」页的
     *       「隐藏物品管理器界面」之后，并在它们前面插一行黄色纯文字「Recipe Book Is Pain」。</li>
     * </ol>
     *
     * <p><b>为什么搬条目而不是搬字段</b>：Cloth 的子对象（{@code @TransitiveObject}）条目
     * 只能落在父字段所属的类别里（类别只在顶层字段上解析），把字段升到顶层会让 TOML 路径从
     * {@code [rbip] enableTabPage} 变成顶层键、老配置值失效。所以这里保持字段原地不动，
     * 只把已经建好的条目对象重排 —— 条目仍绑定原字段，保存逻辑不变。
     * 「Recipe Book Is Pain」那行原本是主开关的 {@code @PrefixText}（会跟着开关一起跑），
     * 现改为「界面」页里的独立文字行。</p>
     */
    private static void relocateRbipEntries(ConfigBuilder builder) {
        ConfigCategory from = builder.getOrCreateCategory(Component.translatable(DEFAULT_CATEGORY_KEY));
        List<Object> defaultEntries = from.getEntries();

        // 1) 主开关：移到 recipeViewerEnabled 那条「§eJust Emulated Items」文字行【之前】。
        //    ⚠️ AutoConfig 的 @PrefixText 并不是选项条目自身的一部分，而是【同组另起的一条
        //    TextListEntry、插在该组第 0 位】（DefaultGuiTransformers 里 ret.add(0, element)，
        //    15.0.140 / 21.11.153 / 26.2.155 三个版本一致）——所以"移到文字行上面"必须再往前
        //    一格，否则会落进文字行与该开关之间（第一版就是这么错的：看起来在文字行下面）。
        //    文字行的 fieldName 是随机 UUID（ConfigEntryBuilderImpl 里 Component.literal(UUID)），
        //    无法按名字识别，只能按类型 + 相邻位置判定。
        Object master = removeByFieldName(defaultEntries, Component.translatable(RBIP_MASTER_OPTION_KEY));
        if (master != null) {
            int viewer = indexOfFieldName(defaultEntries, Component.translatable(VIEWER_ANCHOR_OPTION_KEY));
            boolean prefixed = viewer > 0 && defaultEntries.get(viewer - 1) instanceof TextListEntry;
            int at = viewer < 0 ? defaultEntries.size() : (prefixed ? viewer - 1 : viewer);
            defaultEntries.add(at, master);
        }

        // 2) 两个子开关：搬到「界面」页「隐藏物品管理器界面」之后（前面加黄字分节行）
        ConfigCategory to = builder.getOrCreateCategory(Component.translatable(UI_CATEGORY_KEY));
        List<Object> moved = new ArrayList<>();
        for (String key : RBIP_MOVED_OPTION_KEYS) {
            Object entry = removeByFieldName(defaultEntries, Component.translatable(key));
            if (entry != null) moved.add(entry);
        }
        if (moved.isEmpty()) return;
        List<Object> toInsert = new ArrayList<>();
        toInsert.add(textRow(builder, RBIP_SECTION_LABEL_KEY));
        toInsert.addAll(moved);
        List<Object> target = to.getEntries();
        int anchor = indexOfFieldName(target, Component.translatable(UI_ANCHOR_OPTION_KEY));
        target.addAll(anchor < 0 ? target.size() : anchor + 1, toInsert);
    }

    /** 黄色纯文字行（分节标题）。 */
    private static Object textRow(ConfigBuilder builder, String langKey) {
        return builder.entryBuilder()
                .startTextDescription(Component.translatable(langKey).withStyle(ChatFormatting.YELLOW))
                .build();
    }

    private static int indexOfFieldName(List<Object> entries, Component fieldName) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof AbstractConfigListEntry<?> entry && fieldName.equals(entry.getFieldName())) {
                return i;
            }
        }
        return -1;
    }

    private static Object removeByFieldName(List<Object> entries, Component fieldName) {
        int at = indexOfFieldName(entries, fieldName);
        return at < 0 ? null : entries.remove(at);
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
