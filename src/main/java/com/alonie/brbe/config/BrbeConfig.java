package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Root configuration for Better Recipe Book Extended.
 *
 * <p>Sub-configs ({@code InstantCraft},
 * {@code AlternativeRecipes}, {@code Scrolling}) are standalone classes in the
 * same package — same structure as the 1.21.1 branch.  Only
 * {@code RecipeBookIsPain} is nested here (matches 1.21.1's {@code Config}).</p>
 */
@Config(name = "brbe")
public class BrbeConfig implements ConfigData {

    // -- 配方书设置（general 标签）--------------------------------------------

    /** 拼音搜索：在搜索栏输入拼音匹配中文物品名。仅中文语言（zh_*）下显示配置项并默认开启；其他语言强制关闭。 */
    @ConfigEntry.Gui.Tooltip
    public boolean pinyinSearch = false;

    /** 保存配方书上一次的浏览记录（标签 + 页码），下次打开恢复。 */
    @ConfigEntry.Gui.Tooltip
    public boolean saveRecipeBookPosition = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showModName = false;

    @ConfigEntry.Gui.TransitiveObject
    public Scrolling scrolling = new Scrolling();

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.PrefixText
    public boolean recipeViewerEnabled = true;

    /** 配方书模式：只显示配方书内的对象——配方书体系的工作站（合成/烧炼/锻造/
     *  酿造——BRBE 自带酿造配方书）与配方书驱动的模组类别保留；无配方书体系的
     *  工作站（切石/铁砧/研磨）与信息行类别（燃料/堆肥/信息）整体隐藏，对象的
     *  tooltip 也过滤非法工作站图标。默认关闭。 */
    @ConfigEntry.Gui.Tooltip
    public boolean hideNoRecipeBookStationObjects = false;

    /** 「预览模式」：重新开启界面时查询窗口不再恢复，与其他元素交互时也会关闭查询窗口。默认关。 */
    @ConfigEntry.Gui.Tooltip
    public boolean previewMode = false;

    /** 「在配方区使用自然的翻页方向」：开启时鼠标滚轮向前（上滚）＝往后翻页；
     *  关闭（默认）时是旧方向（上滚＝往前翻页）。只作用于**查询窗口配方区**的翻页——
     *  标签条翻页、Alt+滚轮轮循、配方书自身的翻页都不受影响。默认关闭。 */
    @ConfigEntry.Gui.Tooltip
    public boolean naturalPageDirection = false;

    @ConfigEntry.Gui.TransitiveObject
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    @ConfigEntry.Gui.TransitiveObject
    public InstantCraft instantCraft = new InstantCraft();

    // -- 界面设置（ui 标签）----------------------------------------------------

    /** 隐藏生存模式配方书中的3x3配方标记。 */
    @ConfigEntry.Category("ui")
    public boolean hideIncompatibleMark = false;

    /** 解锁新物品时启用小弹跳动画。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBounce = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean keepCentered = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Excluded
    @ConfigEntry.Gui.Tooltip
    public boolean expandedRecipeBook = false;

    /** 鼠标滚轮翻页音效：滚轮翻页（配方区/配方书标签/查询浮层）时播放点击音。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText
    public boolean scrollPageSound = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.TransitiveObject
    public PageAnimation pageAnimation = new PageAnimation();

    /** 「显示设置按钮」原先带一条 {@code @PrefixText} 黄字提示行
     *  （"如果你禁用了以下两个选项，需要通过模组菜单来重新打开它们"），
     *  按用户要求已移除该文字行 —— 注解一并删除，AutoConfig 不再生成该条目。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean settingsButton = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBook = true;

    // -- 配方设置（recipeSettings 标签）----------------------------------------

    /** 启用后自动解锁所有配方，无需先发现即可在配方书中查看。重新进入游戏生效。 */
    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean unlockAll = true;

    /** 默认关（2026-09-23 用户要求）：开启时才在生存模式配方书里放行 3×3 / 环境不兼容配方。 */
    @ConfigEntry.Category("recipeSettings")
    public boolean showAllRecipesInSurvival = false;

    /** 默认开（2026-09-25 用户要求；2026-09-23 曾按要求改为关）：开启 = 移除「仅显示可合成」按钮并让可合成始终置顶。 */
    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip
    public boolean partialCraftingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean partialMarkingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip
    public boolean partialOnlyWhenCarrying = false;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.TransitiveObject
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    // -- 快捷键（keybindings 标签；位于「配方」右侧）----------------------------

    /** 「固定」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 A。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.Tooltip
    public String pinKey = KeybindingCodec.PIN_DEFAULT_RAW;

    /** 「锁定」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 Alt。
     *  按住 = 冻结**指针下那一个**折叠物品的自动轮换（配方书内的配方、功能方块内的
     *  幽灵物品、查询界面/预览里的对象），配合滚轮逐格翻动；松开恢复自动轮换。
     *  GUI 标题「锁定」，tooltip「锁定循环中的折叠物品。」（用户 2026-09-13）；
     *  位置在「固定」下方。 */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Category("keybindings")
    public String cycleLockKey = KeybindingCodec.cycleLockDefaultRaw();

    /** 「查询合成」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 R。
     *  @PrefixText 复制自 recipeViewerEnabled 的信息行（原信息行保留在
     *  「功能」类别，这里为同一行文案的副本）。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public String recipeViewKey = KeybindingCodec.recipeViewDefaultRaw();

    /** 「查询用途」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 U。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.Tooltip
    public String usageViewKey = KeybindingCodec.usageViewDefaultRaw();

    /** 查询界面「配方区行上限」：对象区**一页最多显示的行数**，默认 3。
     *
     *  <p>行上限同时就是**工作站列的对象数量上限**：查询窗口左侧工作站列的行数
     *  由框体高度推导（{@code RecipeViewerOverlay.stationViewRows()} =
     *  {@code (boxH - 8) / 25}），而框体高度 = 本页行数 × 25 + 8，本页行数恒
     *  ≤ 行上限 —— 所以列里最多只会出现"行上限"个对象，无需额外钳制。
     *
     *  <p>GUI：无 tooltip，只接受整型（运行时会夹紧到 1–64）；位置在「查询用途」
     *  下方、「配方区列上限」上方。 */
    @ConfigEntry.Category("keybindings")
    public int recipeViewerRowLimit = 3;

    /** 查询界面「配方区列上限」：对象区**一页最多显示的列数**，默认 7。
     *
     *  <p>列上限同时就是**底部标签的数量上限**（标签条一次最多显示"列上限"个标签；
     *  改前还额外写死了 10 这个硬上限，已按用户 2026-09-13 的要求去掉）。
     *  唯一的例外是**顶部元素**：标题栏
     *  整行（标题文字 + 旁边翻页键的占位）的加列优先级高于本上限，放不下时会继续
     *  创建列把窗口撑宽 —— 标签条随后也能用上这些多出来的列（即标签数**临时突破**
     *  列上限）。
     *
     *  <p>GUI：无 tooltip，只接受整型（运行时会夹紧到 1–64）；位置在「配方区行
     *  上限」下方。 */
    @ConfigEntry.Category("keybindings")
    public int recipeViewerColumnLimit = 7;

    // -- 「快捷键&数值」页底部的数值项（分节标题「音效与动画」由 ConfigTipsHelper 注入）----

    /** 翻页音效音量（0.0–1.0，默认 1.0 = 原生音量），可在「音乐与声音」界面调节。 */
    @ConfigEntry.Category("keybindings")
    public float pageFlipVolume = 1.0f;

    /** 配方书翻页动画时长（秒）。 */
    @ConfigEntry.Category("keybindings")
    public float pageAnimationDuration = 0.5f;

    // -- 杂项（miscellaneous 标签）--------------------------------------------

    /** 隐藏配置界面的Tips：打开时隐藏「功能」页面顶部的轮循提示行。 */
    @ConfigEntry.Category("miscellaneous")
    @ConfigEntry.Gui.Tooltip
    public boolean hideConfigTips = false;

    /** 隐藏暂停菜单的配置界面入口：打开时暂停菜单图标行的 BRBE 配置按钮不再显示。默认关。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hidePauseMenuConfigEntry = false;

    /** 隐藏配置界面顶部的标题区域：**默认开** —— 关掉它就恢复 Cloth 原来的标题带
     *  （y=18 的界面标题文字 + 上方那 41px 留白）。无 tooltip。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hideConfigTitleBand = true;

    /** 隐藏配置界面两侧的文字：打开时左右两条竖排装饰文字（{@code ConfigScreenSideText}）
     *  不再绘制。无 tooltip，默认关。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hideConfigSideText = false;

    // -- Inner config class ---------------------------------------------------

    public static class PageAnimation {
        public boolean pageAnimationEnabled = true;
    }

    public static class RecipeBookIsPain implements ConfigData {
        /** 主开关（留在「功能」页）。原 `@PrefixText`「§eRecipe Book Is Pain」黄字行
         *  已改为「界面」页里的独立纯文字行，与下面两个子开关一起搬过去 —— 见
         *  {@code ConfigTipsHelper.relocateRbipEntries}。字段仍留在本子对象里，
         *  故 TOML 路径保持 {@code [rbip]} 不变（玩家配置不失效）。 */
        @ConfigEntry.Gui.Tooltip
        public boolean enableRecipeBookIsPain = true;

        public boolean enableTabPage = true;

        /** 隐藏翻页按钮：打开时 RBIP 标签栏的翻页按钮（书左侧的两个箭头）不再显示，
         *  其位置也不再吞掉点击；标签区域的滚轮翻页不受影响。默认关。 */
        public boolean hideTabPageButtons = false;
    }

    @Override
    public void validatePostLoad() {
        this.expandedRecipeBook = false;
    }
}
