package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.ConfigTipCarousel;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.ConfigScreenProvider;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigTabButton;
import me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget;
import me.shedaniel.math.Rectangle;
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
 * <p>通过 {@link #registerCarousel} 注册 {@link ConfigTipCarousel}（文案池 +
 * 显示位置）。打开配置界面时遍历所有注册的轮循行，按各自的位置插入显示行：默认注册的
 * "功能 tips" 行是**屏幕级**的 —— 插在搜索栏之上、切到任何类别页都可见（见
 * {@link #installScreenWideTips}）；其余轮循行仍插在自己绑定类别的最上方。
 * 每次打开每个轮循行随机选一条且避免相邻重复。</p>
 *
 * <p>所有配置界面入口统一走 {@link #openConfigScreen}（自动带轮循行）。</p>
 */
public final class ConfigTipsHelper {

    private static final List<ConfigTipCarousel> CAROUSELS = new ArrayList<>();

    static {
        // 默认："提示：xxx"功能 tips 轮循行 —— **屏幕级**：显示在搜索栏之上，所有类别页都可见
        // （category(...) 仅作标识；屏幕级由 .screenWide(true) 决定）
        registerCarousel(ConfigTipCarousel.builder()
                .category(Component.translatable("text.autoconfig.brbe.category.default"))
                .screenWide(true)
                .prefix(Component.translatable("brbe.gui.tip.prefix"))
                // 文案池：序号保留历史编号 —— 原来的 4 / 5 已按用户要求移除、不补位，
                // 这样 "tip.N" 在译文/文档/对话里始终指同一条（tip.2 与 tip.9 也按用户定稿改过文案）。
                .tipKeys(List.of("brbe.gui.tip.1", "brbe.gui.tip.2", "brbe.gui.tip.3",
                        "brbe.gui.tip.6", "brbe.gui.tip.7", "brbe.gui.tip.8", "brbe.gui.tip.9"))
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
    public static void openConfigScreen(Class configClass, Screen parent) {
        Screen screen = buildConfigScreen(configClass, parent);
        if (screen != null && screen != parent) Minecraft.getInstance().setScreen(screen);
    }

    /**
     * 构建（并整理）配置界面并返回 —— 供需要拿到 {@code Screen} 的入口使用
     * （暂停菜单按钮 / ModMenu 配置按钮 / NeoForge 的 {@code IConfigScreenFactory}）。
     *
     * <p>⚠️ <b>所有配置界面入口都必须走这里或 {@link #openConfigScreen}</b>：直接调
     * {@code AutoConfig.getConfigScreen(...)} 会拿到**未经整理**的界面（没有轮循提示行、
     * 没有分节黄字行、条目也是字段声明顺序）——2026-09-12 用户实测：暂停菜单入口进去后
     * "大量配置项顺序错乱"，就是因为它当时绕过了这里。</p>
     *
     * <p>整理过程抛异常时**回退到未整理的原始界面**并打 ERROR 日志 —— 宁可顺序没整理好，
     * 也不能让入口按钮点了没反应（同日的教训：relocateEntries 越界异常抛在按钮的
     * mouseClicked 里，按钮表现为"无反应"）。Cloth 缺失时返回 {@code parent}。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Screen buildConfigScreen(Class configClass, Screen parent) {
        try {
            var provider = (ConfigScreenProvider) AutoConfig.getConfigScreen(configClass, parent);
            // 显式声明 lambda 的目标类型：provider 是 raw 类型（(ConfigScreenProvider) 强转），
            // 直接把 lambda 内联进 setBuildFunction 会把参数推断成 Object（编译报错）。
            // 屏幕级轮循行的条目只能在 build 回调里建（那里才有 ConfigBuilder），
            // 拿到屏幕后再挂到它的 afterInitConsumer 上（首次打开时 init() 才会跑）。
            List<AbstractConfigListEntry<?>> screenWideRows = new ArrayList<>();
            java.util.function.Function<ConfigBuilder, Screen> buildFn = builder -> {
                addCarousels(builder, screenWideRows);
                addSectionLabels(builder);
                relocateEntries(builder);
                // 顶部标题带移除：Cloth 把标题文字写死在 y=18（无法平移），所以直接清空标题；
                // 腾出的那 41px 由 installScreenTweaks 把标签行与列表整体上移（removeTitleBand）。
                builder.setTitle(Component.empty());
                return builder.build();
            };
            provider.setBuildFunction(buildFn);
            Screen built = provider.get();
            installScreenTweaks(built, screenWideRows);
            return built;
        } catch (NoClassDefFoundError e) {
            return parent;                       // Cloth Config 不可用
        } catch (RuntimeException e) {
            BetterRecipeBook.LOGGER.error("[BRBE] 配置界面条目整理失败，回退到未整理的默认顺序", e);
            try {
                return (Screen) AutoConfig.getConfigScreen(configClass, parent).get();
            } catch (Throwable t) {
                return parent;
            }
        }
    }

    /** 分节标题行（黄色纯文字）：「功能」页页首的「配方书」、「快捷键&数值」页页首的「通用」
     *  与数值项之前的「音效与动画」。与轮循 tips 无关，故不受 {@code hideConfigTips} 影响。 */
    private static final String SECTION_CATEGORY_KEY = "text.autoconfig.brbe.category.keybindings";
    private static final String SECTION_LABEL_KEY = "brbe.gui.section.soundAnimation";
    /** 分节标题的锚点：插在这一项之前（识别按 AutoConfig 的 option i18n 键，与语言无关）。 */
    private static final String SECTION_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.pageFlipVolume";
    /** 页首分节标题「通用」：插在「固定」（pinKey）之前。 */
    private static final String GENERAL_SECTION_LABEL_KEY = "brbe.gui.section.general";
    private static final String GENERAL_SECTION_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.pinKey";
    /** 「功能」页页首分节标题「配方书」的锚点：插在「拼音搜索」之前
     *  （文案键复用「界面」页那条 {@link #RECIPE_BOOK_SECTION_LABEL_KEY}，两处同一行文字）。 */
    private static final String PINYIN_SECTION_ANCHOR_OPTION_KEY =
            "text.autoconfig.brbe.option.pinyinSearch";

    /**
     * 插三条纯文字分节标题行（黄色）：「功能」页页首的「配方书」（「拼音搜索」之前）、
     * 「快捷键&数值」页页首的「通用」（「固定」之前）与数值项之前的「音效与动画」。
     *
     * <p>Cloth 的类别条目由 AutoConfig 按字段声明顺序生成，标题行只能在这里插进条目列表：
     * 定位方式是把条目的 {@code getFieldName()} 与 {@code text.autoconfig.brbe.option.<字段名>}
     * 的翻译组件比较（AutoConfig 的字段条目名就是 {@code Component.translatable(optionI13n)}）。
     * 找不到锚点时：页首那两条退化为整页第一条（本来就是"页首"语义），数值那条退化为页尾。
     * ⚠️「拼音搜索」这一锚点在**非中文语言**下必然找不到 —— {@code PinyinSearchGuiRegistrar}
     * 的 provider 在该语言下返回空表（整条不显示），此时走的正是"退化为页首"这条路径。</p>
     */
    private static void addSectionLabels(ConfigBuilder builder) {
        // ⓪ 「功能」页页首的「配方书」黄字行：插在「拼音搜索」之前（用户 2026-09-12 要求）。
        //    该类别此刻还没有任何被搬走的条目，且 relocateEntries 用字段名定位、不认下标，
        //    所以先插它不会影响后面的搬运。
        ConfigCategory defaultCategory =
                builder.getOrCreateCategory(Component.translatable(DEFAULT_CATEGORY_KEY));
        List<Object> defaultEntries = defaultCategory.getEntries();
        int pinyinAt = indexOfFieldName(defaultEntries,
                Component.translatable(PINYIN_SECTION_ANCHOR_OPTION_KEY));
        defaultEntries.add(pinyinAt < 0 ? 0 : pinyinAt,
                textRow(builder, RECIPE_BOOK_SECTION_LABEL_KEY));

        ConfigCategory category = builder.getOrCreateCategory(Component.translatable(SECTION_CATEGORY_KEY));
        List<Object> entries = category.getEntries();
        // ① 页首「通用」：插在「固定」之前
        int generalAt = indexOfFieldName(entries, Component.translatable(GENERAL_SECTION_ANCHOR_OPTION_KEY));
        entries.add(generalAt < 0 ? 0 : generalAt, textRow(builder, GENERAL_SECTION_LABEL_KEY));
        // ② 数值分节的「音效与动画」：插在「音效音量」之前
        //    ⚠️ 必须在 ① 之后重新查下标——① 已经把后面的条目整体后移了一位。
        int at = indexOfFieldName(entries, Component.translatable(SECTION_ANCHOR_OPTION_KEY));
        entries.add(at < 0 ? entries.size() : at, textRow(builder, SECTION_LABEL_KEY));
    }

    // ── 「界面」页的「Recipe Book Is Pain」小节（条目搬运）──────────────────────

    /** RBIP 的两个子开关（在 {@code [rbip]} 子对象里）要在「界面」页显示的分区锚点。 */
    private static final String UI_CATEGORY_KEY = "text.autoconfig.brbe.category.ui";
    private static final String DEFAULT_CATEGORY_KEY = "text.autoconfig.brbe.category.default";
    /** RBIP 分区（黄字行 + 两个子开关）在「界面」页的落脚点：插在「配方书居中」之后。 */
    private static final String RBIP_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.keepCentered";
    private static final String RBIP_SECTION_LABEL_KEY = "brbe.gui.section.recipeBookIsPain";
    /** 「界面」页「配方书」分节的黄色标题行：插在「显示一键制作按钮」之前。 */
    private static final String RECIPE_BOOK_SECTION_LABEL_KEY = "brbe.gui.section.recipeBook";
    /** 依次搬过去的条目（相对顺序即此表顺序）。 */
    private static final List<String> RBIP_MOVED_OPTION_KEYS = List.of(
            "text.autoconfig.brbe.option.rbip.enableTabPage",
            "text.autoconfig.brbe.option.rbip.hideTabPageButtons");
    /** RBIP 主开关：移到「§eJust Emulated Items（物品管理器）」文字行（{@code recipeViewerEnabled} 项）之前。 */
    private static final String RBIP_MASTER_OPTION_KEY = "text.autoconfig.brbe.option.rbip.enableRecipeBookIsPain";
    private static final String VIEWER_ANCHOR_OPTION_KEY = "text.autoconfig.brbe.option.recipeViewerEnabled";
    /** 一键制作子配置的两个条目：「启用一键制作」跟到主开关下面，「显示一键制作按钮」去「界面」页顶部。 */
    private static final String INSTANT_CRAFT_ENABLED_OPTION_KEY = "text.autoconfig.brbe.option.instantCraft.enabled";
    private static final String INSTANT_CRAFT_BUTTON_OPTION_KEY = "text.autoconfig.brbe.option.instantCraft.showButton";
    /** 「显示设置按钮」「启用配方书」：一起搬到「Recipe Book Is Pain」黄字行之前（相对顺序不变）。 */
    private static final String SETTINGS_BUTTON_OPTION_KEY = "text.autoconfig.brbe.option.settingsButton";
    private static final String ENABLE_BOOK_OPTION_KEY = "text.autoconfig.brbe.option.enableBook";
    private static final String RECIPE_SETTINGS_CATEGORY_KEY = "text.autoconfig.brbe.category.recipeSettings";
    /** 「启用解锁弹跳动画」：搬到「配方书翻页动画」（{@code pageAnimation.pageAnimationEnabled}）之后。
     *  ⚠️ 该字段三分支不同 —— 26.2/1.21.11 是顶层 {@code enableBounce}，1.21.1 仍在
     *  {@code newRecipes} 子对象里（{@code newRecipes.enableBounce}）。子对象条目跟随
     *  **父字段**的类别（反编译 {@code ConfigScreenProvider} 核实：类别只由顶层字段的
     *  {@code @ConfigEntry.Category} 决定），所以按候选键逐一找，条目可能在任何一页。 */
    private static final List<String> ENABLE_BOUNCE_OPTION_KEYS = List.of(
            "text.autoconfig.brbe.option.enableBounce",
            "text.autoconfig.brbe.option.newRecipes.enableBounce");
    private static final String PAGE_ANIMATION_ENABLED_OPTION_KEY =
            "text.autoconfig.brbe.option.pageAnimation.pageAnimationEnabled";

    /**
     * 按期望的最终布局重排 GUI 条目（只重排条目对象，字段与 TOML 路径都保持原样）：
     * <ol>
     *   <li>「启用Recipe Book Is Pain」→「§eJust Emulated Items（物品管理器）」文字行之前；</li>
     *   <li>「启用一键制作」→ 紧随「启用Recipe Book Is Pain」之后；</li>
     *   <li>「显示一键制作按钮」→「界面」页顶部，并在它上面插一行黄色纯文字「配方书」；</li>
     *   <li>「启用上侧和下侧的标签」「隐藏翻页按钮」→「界面」页「配方书居中」之后
     *       （并在它们前面插一行黄色纯文字「Recipe Book Is Pain」）；</li>
     *   <li>「显示设置按钮」「启用配方书」→「Recipe Book Is Pain」黄字行之前，两者相对顺序不变；</li>
     *   <li>「启用解锁弹跳动画」→「配方书翻页动画」之后（跨页：1.21.1 上它原本在「配方」页）。</li>
     * </ol>
     *
     * <p><b>为什么搬条目而不是搬字段</b>：Cloth 的子对象（{@code @TransitiveObject}）条目
     * 只能落在父字段所属的类别里（类别只在顶层字段上解析），把字段升到顶层会让 TOML 路径从
     * {@code [rbip] enableTabPage} / {@code [instantCraft] enabled} 变成顶层键、老配置值失效。
     * 所以这里保持字段原地不动，只把已经建好的条目对象重排 —— 条目仍绑定原字段，保存逻辑不变。
     * 「Recipe Book Is Pain」那行原本是主开关的 {@code @PrefixText}（会跟着开关一起跑），
     * 现改为「界面」页里的独立文字行。</p>
     */
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
        // 3) 「显示一键制作按钮」：「界面」页顶部（跨类别：它原本在「功能」页的 instantCraft 组里）
        moveToTopOf(uiEntries, defaultEntries, INSTANT_CRAFT_BUTTON_OPTION_KEY);
        // 3b) 它上面那条黄色纯文字「配方书」——本节（配方书相关开关）的分节标题行
        int craftButtonAt = indexOfFieldName(uiEntries, Component.translatable(INSTANT_CRAFT_BUTTON_OPTION_KEY));
        uiEntries.add(craftButtonAt < 0 ? 0 : craftButtonAt, textRow(builder, RECIPE_BOOK_SECTION_LABEL_KEY));
        // 4) RBIP 两个子开关：搬到「界面」页「配方书居中」之后（前置黄字分节行）
        List<Object> moved = new ArrayList<>();
        for (String key : RBIP_MOVED_OPTION_KEYS) {
            Object entry = removeByFieldName(defaultEntries, Component.translatable(key));
            if (entry != null) moved.add(entry);
        }
        Object rbipSectionRow = null;
        if (!moved.isEmpty()) {
            rbipSectionRow = textRow(builder, RBIP_SECTION_LABEL_KEY);
            List<Object> toInsert = new ArrayList<>();
            toInsert.add(rbipSectionRow);
            toInsert.addAll(moved);
            // 锚点条目自身没有 @PrefixText，所以 +1 就落在它下面（黄字行是独立插入的）。
            int anchor = indexOfFieldName(uiEntries, Component.translatable(RBIP_ANCHOR_OPTION_KEY));
            uiEntries.addAll(anchor < 0 ? uiEntries.size() : anchor + 1, toInsert);
        }
        // 5) 「显示设置按钮」「启用配方书」→「Recipe Book Is Pain」黄字行【之前】，相对顺序不变。
        //    该行是 textRow 现造的，字段名是随机 UUID，只能按**对象引用**定位（见 moveBeforeEntry）。
        if (rbipSectionRow != null) {
            moveBeforeEntry(uiEntries, SETTINGS_BUTTON_OPTION_KEY, rbipSectionRow);
            moveAfter(uiEntries, ENABLE_BOOK_OPTION_KEY, SETTINGS_BUTTON_OPTION_KEY);
        }
        // 6) 「启用解锁弹跳动画」→「配方书翻页动画」之后（条目可能在 default / ui / recipeSettings
        //    任意一页里，所以三张列表都参与查找；锚点固定在「界面」页）
        List<Object> recipeEntries =
                builder.getOrCreateCategory(Component.translatable(RECIPE_SETTINGS_CATEGORY_KEY)).getEntries();
        moveAfterFirstFound(List.of(defaultEntries, uiEntries, recipeEntries),
                ENABLE_BOUNCE_OPTION_KEYS, PAGE_ANIMATION_ENABLED_OPTION_KEY);
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

    /** 把 {@code optionKey} 的条目挪到 {@code anchorEntry} 这条**已有条目对象**之前。
     *  纯文字行（{@code TextListEntry}）的字段名是随机 UUID，{@link #indexOfFieldName} 定位不到，
     *  只能按对象引用查找；条目或锚点缺失时静默不动（与其它 move* 一致）。 */
    private static void moveBeforeEntry(List<Object> entries, String optionKey, Object anchorEntry) {
        Object entry = removeByFieldName(entries, Component.translatable(optionKey));
        if (entry == null) return;
        int at = entries.indexOf(anchorEntry);
        entries.add(at < 0 ? entries.size() : at, entry);
    }

    /** 把 {@code optionKey} 的条目挪到 {@code to} 类别最前面。
     *  条目可能本来就在 {@code to} 里（同类别置顶）、也可能在 {@code from} 里（跨类别搬运），
     *  两个列表都找一遍 —— 只传一个列表时，传错会让整个操作**静默不生效**（曾经的 bug：
     *  「显示一键制作按钮」传了「界面」列表，而条目实际在「功能」列表里）。 */
    private static void moveToTopOf(List<Object> to, List<Object> from, String optionKey) {
        Component name = Component.translatable(optionKey);
        Object entry = removeByFieldName(to, name);
        if (entry == null) entry = removeByFieldName(from, name);
        if (entry != null) to.add(0, entry);
    }

    /** 把候选键里的条目（第一个找到的）挪到 {@code anchorKey} 条目之后；三张类别列表都参与查找
     *  （子对象条目跟随父字段类别，落哪一页随分支而变），锚点也必须能在某张列表里找到。
     *  锚点或条目找不到时**什么都不做**（保持条目原位，不产生半成品布局）。 */
    private static void moveAfterFirstFound(List<List<Object>> lists, List<String> optionKeys, String anchorKey) {
        Component anchor = Component.translatable(anchorKey);
        for (String optionKey : optionKeys) {
            Component name = Component.translatable(optionKey);
            for (List<Object> from : lists) {
                Object entry = removeByFieldName(from, name);
                if (entry == null) continue;
                // ⚠️ 必须**先摘出条目、再查锚点下标**：条目与锚点同在一张列表、且条目排在锚点
                //    之前时，摘除会让锚点整体前移一位；沿用摘除前算好的下标就会 add(size + 1)
                //    越界。2026-09-12 实测崩溃：锚点「配方书翻页动画」是「界面」页最后一条，
                //    而「启用解锁弹跳动画」在它前面 → IndexOutOfBoundsException: Index: 14,
                //    Size: 13，异常抛在按钮的 mouseClicked 里 → 配方书内的设置按钮点了没反应。
                for (List<Object> anchorList : lists) {
                    int at = indexOfFieldName(anchorList, anchor);
                    if (at >= 0) {
                        anchorList.add(at + 1, entry);
                        return;
                    }
                }
                from.add(entry);   // 锚点找不到：放回原位（不产生半成品布局）
                return;
            }
        }
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

    /** 建轮循提示行。{@code screenWide} 的行（见 {@link ConfigTipCarousel#screenWide()}）收进
     *  {@code screenWide} 列表，由 {@link #installScreenWideTips} 插到**搜索栏之上**（所有类别页
     *  可见）；其余仍按老规矩插到各自绑定类别的第一条。 */
    private static void addCarousels(ConfigBuilder builder, List<AbstractConfigListEntry<?>> screenWide) {
        if (BetterRecipeBook.config.hideConfigTips) return;
        for (ConfigTipCarousel carousel : CAROUSELS) {
            if (!carousel.hasTips()) continue;
            int idx = carousel.nextTipIndex();
            Component line = carousel.prefix() == null
                    ? carousel.tipAt(idx).copy().withStyle(carousel.style())
                    : carousel.prefix().copy().withStyle(carousel.style())
                            .append(carousel.tipAt(idx).copy().withStyle(carousel.style()));
            AbstractConfigListEntry<?> row = builder.entryBuilder().startTextDescription(line).build();
            if (carousel.screenWide()) {
                screenWide.add(row);
                continue;
            }
            ConfigCategory category = builder.getOrCreateCategory(carousel.categoryTitle());
            category.getEntries().add(0, row);
        }
    }

    // ── 屏幕级轮循行：搜索栏之上、所有类别页可见 ─────────────────────────────

    /**
     * 把屏幕级轮循行插到「搜索栏之上」，并在**所有类别页**显示（用户 2026-09-12 要求）。
     *
     * <p><b>为什么位置是"列表第 0 位"</b>：Cloth 的配置界面里搜索栏**不是屏幕级控件**，而是
     * **列表的第一个内容行** —— {@code ClothConfigScreen.init()} 往 {@code listWidget.children()}
     * 里依次塞 {@code EmptyEntry(5)} → {@code SearchFieldEntry} → {@code EmptyEntry(5)} →
     * 当前类别的条目。所以"搜索栏之上"就是这个列表的第 0 位（插在 5px 空行与搜索栏之前）。</p>
     *
     * <p><b>为什么必须挂在 {@code setAfterInitConsumer}</b>：类别切换走的是
     * {@code ClothConfigTabButton.onPress → screen.init(width, height)}，而 {@code init()} 每次都会
     * **新建一个 ListWidget**（旧列表连同插入过的行一起被丢弃），窗口缩放同理；{@code init()} 末尾
     * 会调用 {@code afterInitConsumer}，在那里重新插入即可一次覆盖「首次打开 / 切类别 / 缩放」。
     * 条目实例只在打开界面时建一次 → <b>切类别不会重新随机 tip</b>（同一行文案在整次打开期间稳定）。</p>
     *
     * <p><b>搜索不会把它滤掉</b>：{@code SearchFieldEntry} 的过滤器要求
     * {@code entry.isDisplayed() && screen.matchesSearch(entry.getSearchTags())}，而
     * {@code matchesSearch} 对**没有搜索标签**的条目恒返回 true（{@code !tags.hasNext()} 分支），
     * 文字行正好没有标签 —— 输入搜索词时它仍留在搜索栏上方。</p>
     *
     * <p>{@code setScreen} 必须调用：{@code AbstractConfigEntry} 的 {@code screen} 字段被
     * {@code wrapLines(...)} / {@code addTooltip(...)} 使用（类别内的条目由
     * {@code ClothConfigScreen} 构造器统一设置，我们这条不在任何类别里）。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void installScreenTweaks(Screen screen, List<AbstractConfigListEntry<?>> rows) {
        if (!(screen instanceof AbstractConfigScreen configScreen)) return;
        java.util.function.Consumer<Screen> install = s -> {
            if (!(s instanceof ClothConfigScreen cloth)) {
                warnNonClothScreenOnce(s);
                return;
            }
            if (!rows.isEmpty()) {
                List<AbstractConfigEntry<AbstractConfigEntry<?>>> children = cloth.listWidget.children();
                // 逆序插回：注册顺序 = 自上而下的显示顺序（先注册的最靠上）
                for (int i = rows.size() - 1; i >= 0; i--) {
                    AbstractConfigEntry row = rows.get(i);
                    if (children.contains(row)) continue;      // 幂等（同一实例已在列表里）
                    row.setScreen(cloth);
                    children.add(0, row);
                }
            }
            // 「隐藏配置界面的Tips」开着时上面那段不跑，标题带照样要移除 → 这里不早退。
            removeTitleBand(cloth);
        };
        configScreen.setAfterInitConsumer(install);
    }

    // ── 顶部标题带移除（2026-09-12（十九））──────────────────────────────────

    /** 标签行贴顶后的上边距（Cloth 原本是 41 = 标题带高度；留 2px，与标签按钮自身的内边距一致）。 */
    private static final int TABS_TOP_MARGIN = 2;

    /** Cloth 私有布局字段的反射缓存 —— Cloth 是**可选依赖**（缺失时 BRBE 照常运行），
     *  所以这里不引 mixin 而是反射取用；字段取不到就退化为"保留标题带"并打一次日志。 */
    private static final java.util.Map<String, java.lang.reflect.Field> CLOTH_LAYOUT_FIELDS =
            new java.util.HashMap<>();

    private static boolean titleBandWarned;

    private static java.lang.reflect.Field clothLayoutField(String name)
            throws NoSuchFieldException {
        java.lang.reflect.Field cached = CLOTH_LAYOUT_FIELDS.get(name);
        if (cached != null) return cached;
        java.lang.reflect.Field field = ClothConfigScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        CLOTH_LAYOUT_FIELDS.put(name, field);
        return field;
    }

    /**
     * 去掉配置界面顶部的**标题带**：Cloth 把界面标题固定画在 y=18、标签条固定在 y=41..65、
     * 列表从 y=70 起 —— 标题占掉的那 41px 在大 GUI 缩放下很浪费（用户 2026-09-12 要求
     * "类别行直接放在顶部"）。标题**文字**的坐标在 Cloth 里写死、无法平移，所以文本改在
     * {@link #buildConfigScreen} 里用 {@code builder.setTitle(Component.empty())} 清空；
     * 这里把**标签条与列表整体上移**同样的距离。
     *
     * <p>四处必须一起动，漏一处就是"按钮跑到条带外面"或"被裁掉"（Cloth 的
     * {@code extractRenderState} 用 {@code tabsBounds} 开 scissor 裁标签）：
     * ① 标签按钮 —— {@code childrenL()} 里的 {@link ClothConfigTabButton}；
     * ② 左右滚动按钮 {@code buttonLeftTab} / {@code buttonRightTab}（私有字段）；
     * ③ 三个命中/绘制矩形 {@code tabsBounds} / {@code tabsLeftBounds} / {@code tabsRightBounds}
     *   （私有字段；{@link Rectangle} 的 x/y/width/height 是 public 可变字段）；
     * ④ 列表控件 —— {@code listWidget} 本身 public，其 {@code top}/{@code bottom} 字段与
     *   {@code updateSize(...)} 也是 public，直接改。
     *
     * <p>上移量按**实际布局**算（标签按钮当前 y − {@link #TABS_TOP_MARGIN}），不写死 41：
     * 三个 Cloth 版本的常量万一不同也能自适应。没有标签行（单类别界面）时不动。
     * 每次 {@code init()}（打开 / 切类别 / 缩放窗口）都会重跑，位置始终一致。
     */
    private static void removeTitleBand(ClothConfigScreen cloth) {
        int tabY = Integer.MAX_VALUE;
        for (Object child : cloth.childrenL()) {
            if (child instanceof ClothConfigTabButton tab) {
                tabY = Math.min(tabY, tab.getY());
            }
        }
        if (tabY == Integer.MAX_VALUE) return;                 // 无标签行：保持 Cloth 原样
        int shift = tabY - TABS_TOP_MARGIN;
        if (shift <= 0) return;                                // 已经贴顶，无需再动
        try {
            for (Object child : cloth.childrenL()) {
                if (child instanceof ClothConfigTabButton tab) {
                    tab.setY(tab.getY() - shift);
                }
            }
            for (String name : new String[] {"buttonLeftTab", "buttonRightTab"}) {
                shiftWidgetY(clothLayoutField(name).get(cloth), -shift);
            }
            for (String name : new String[] {"tabsBounds", "tabsLeftBounds", "tabsRightBounds"}) {
                if (clothLayoutField(name).get(cloth) instanceof Rectangle rect) {
                    rect.y -= shift;
                }
            }
        } catch (ReflectiveOperationException e) {
            if (!titleBandWarned) {
                titleBandWarned = true;
                BetterRecipeBook.LOGGER.warn("[BRBE] 配置界面顶部标题带未能移除（Cloth 布局字段有变？）：{}",
                        e.toString());
            }
            return;
        }
        DynamicEntryListWidget<?> list = (DynamicEntryListWidget<?>) (Object) cloth.listWidget;
        list.updateSize(list.width, list.height, list.top - shift, list.bottom);
    }

    /** 反射调 {@code setY(int)}：滚动按钮的编译期类型在两套映射下不同
     *  （Mojang 的 AbstractWidget / Yarn 的 ClickableWidget），反射就能写一份代码。 */
    private static void shiftWidgetY(Object widget, int delta) throws ReflectiveOperationException {
        if (widget == null) return;
        java.lang.reflect.Method get = widget.getClass().getMethod("getY");
        java.lang.reflect.Method set = widget.getClass().getMethod("setY", int.class);
        set.invoke(widget, ((Integer) get.invoke(widget)) + delta);
    }

    /** 一次性告警：屏幕级轮循行只认 {@code ClothConfigScreen}（Cloth 的 globalized 变体
     *  {@code GlobalizedClothConfigScreen} 是它的兄弟类、没有 {@code listWidget}）。真出现这种
     *  屏幕时行会装不上 —— 打一次日志说明原因，而不是静默丢失。 */
    private static boolean warnedNonClothScreen;

    private static void warnNonClothScreenOnce(Screen screen) {
        if (warnedNonClothScreen) return;
        warnedNonClothScreen = true;
        BetterRecipeBook.LOGGER.warn("[BRBE] 屏幕级轮循行未安装：{} 不是 ClothConfigScreen",
                screen.getClass().getName());
    }
}
