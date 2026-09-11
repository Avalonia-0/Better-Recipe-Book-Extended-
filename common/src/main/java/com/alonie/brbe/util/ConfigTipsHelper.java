package com.alonie.brbe.util;

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
                .tipKeys(List.of("brbe.gui.tip.1", "brbe.gui.tip.2", "brbe.gui.tip.3", "brbe.gui.tip.4",
                        "brbe.gui.tip.5", "brbe.gui.tip.6", "brbe.gui.tip.7",
                        "brbe.gui.tip.8", "brbe.gui.tip.9"))
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
    /** 隐藏配置界面 Tips（hideConfigTips 开关，1.21.11 对齐）。 */
    public static boolean hidesTips() {
        return com.alonie.brbe.BetterRecipeBook.config != null
                && com.alonie.brbe.BetterRecipeBook.config.hideConfigTips;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void openConfigScreen(Class configClass, Screen parent) {
        try {
            var provider = (ConfigScreenProvider) AutoConfig.getConfigScreen(configClass, parent);
            java.util.function.Function<ConfigBuilder, Screen> buildFn = builder -> {
                addCarousels(builder);
                addSectionLabels(builder);
                relocateEntries(builder);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
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
    /** 一键制作子配置的两个条目：「启用一键制作」跟到主开关下面，「显示一键制作按钮」去「界面」页顶部。 */
    private static final String INSTANT_CRAFT_ENABLED_OPTION_KEY = "text.autoconfig.brbe.option.instantCraft.enabled";
    private static final String INSTANT_CRAFT_BUTTON_OPTION_KEY = "text.autoconfig.brbe.option.instantCraft.showButton";

    /**
     * 按期望的最终布局重排 GUI 条目（只重排条目对象，字段与 TOML 路径都保持原样）：
     * <ol>
     *   <li>「启用Recipe Book Is Pain」→「§eJust Emulated Items」文字行之前；</li>
     *   <li>「启用一键制作」→ 紧随「启用Recipe Book Is Pain」之后；</li>
     *   <li>「显示一键制作按钮」→「界面」页顶部（第 0 条）；</li>
     *   <li>「启用上侧和下侧的标签」「隐藏翻页按钮」→「界面」页「隐藏物品管理器界面」之后
     *       （并在它们前面插一行黄色纯文字「Recipe Book Is Pain」）。</li>
     * </ol>
     *
     * <p><b>为什么搬条目而不是搬字段</b>：Cloth 的子对象（{@code @TransitiveObject}）条目
     * 只能落在父字段所属的类别里（类别只在顶层字段上解析），把字段升到顶层会让 TOML 路径从
     * {@code [rbip] enableTabPage} / {@code [instantCraft] enabled} 变成顶层键、老配置值失效。
     * 所以这里保持字段原地不动，只把已经建好的条目对象重排 —— 条目仍绑定原字段，保存逻辑不变。
     * 「Recipe Book Is Pain」那行原本是主开关的 {@code @PrefixText}（会跟着开关一起跑），
     * 现改为「界面」页里的独立文字行。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void relocateEntries(ConfigBuilder builder) {
        List<Object> defaultEntries =
                builder.getOrCreateCategory(Component.translatable(DEFAULT_CATEGORY_KEY)).getEntries();
        List<Object> uiEntries =
                builder.getOrCreateCategory(Component.translatable(UI_CATEGORY_KEY)).getEntries();

        // 1) 主开关「启用Recipe Book Is Pain」：插到 recipeViewerEnabled 的 @PrefixText 文字行【之前】。
        //    ⚠️ AutoConfig 的 @PrefixText 不是选项条目自身的一部分，而是同组另起的一条
        //    TextListEntry、插在该组第 0 位（DefaultGuiTransformers: ret.add(0, element)）——
        //    所以"移到文字行上面"必须再往前一格，否则会落进文字行与该开关之间。
        moveBefore(defaultEntries, RBIP_MASTER_OPTION_KEY, VIEWER_ANCHOR_OPTION_KEY, true);
        // 2) 「启用一键制作」：紧随主开关之后
        moveAfter(defaultEntries, INSTANT_CRAFT_ENABLED_OPTION_KEY, RBIP_MASTER_OPTION_KEY);
        // 3) 「显示一键制作按钮」：「界面」页顶部
        moveToTop(uiEntries, INSTANT_CRAFT_BUTTON_OPTION_KEY);
        // 4) RBIP 两个子开关：搬到「界面」页「隐藏物品管理器界面」之后（前置黄字分节行）
        List<Object> moved = new ArrayList<>();
        for (String key : RBIP_MOVED_OPTION_KEYS) {
            Object entry = removeByFieldName(defaultEntries, Component.translatable(key));
            if (entry != null) moved.add(entry);
        }
        if (moved.isEmpty()) return;
        List<Object> toInsert = new ArrayList<>();
        toInsert.add(textRow(builder, RBIP_SECTION_LABEL_KEY));
        toInsert.addAll(moved);
        int anchor = indexOfFieldName(uiEntries, Component.translatable(UI_ANCHOR_OPTION_KEY));
        uiEntries.addAll(anchor < 0 ? uiEntries.size() : anchor + 1, toInsert);
    }

    /** 把 {@code optionKey} 的条目挪到 {@code anchorKey} 条目之前；{@code skipTextRowAbove} 为真时
     *  连锚点条目上面那条 {@code @PrefixText} 文字行一起跳过（即"文字行上面"）。 */
    private static void moveBefore(List<Object> entries, String optionKey, String anchorKey, boolean skipTextRowAbove) {
        Object entry = removeByFieldName(entries, Component.translatable(optionKey));
        if (entry == null) return;
        int at = indexOfFieldName(entries, Component.translatable(anchorKey));
        if (at < 0) {
            entries.add(entry);
            return;
        }
        if (skipTextRowAbove && at > 0 && entries.get(at - 1) instanceof TextListEntry) at--;
        entries.add(at, entry);
    }

    /** 把 {@code optionKey} 的条目挪到 {@code anchorKey} 条目之后。 */
    private static void moveAfter(List<Object> entries, String optionKey, String anchorKey) {
        Object entry = removeByFieldName(entries, Component.translatable(optionKey));
        if (entry == null) return;
        int at = indexOfFieldName(entries, Component.translatable(anchorKey));
        entries.add(at < 0 ? entries.size() : at + 1, entry);
    }

    /** 把 {@code optionKey} 的条目挪到该类别最前面。 */
    private static void moveToTop(List<Object> entries, String optionKey) {
        Object entry = removeByFieldName(entries, Component.translatable(optionKey));
        if (entry != null) entries.add(0, entry);
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
        if (hidesTips()) return;
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
