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

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean hideReiJeiOverlay = false;

    /** 鼠标滚轮翻页音效：滚轮翻页（配方区/配方书标签/查询浮层）时播放点击音。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText
    public boolean scrollPageSound = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.TransitiveObject
    public PageAnimation pageAnimation = new PageAnimation();

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText
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

    @ConfigEntry.Category("recipeSettings")
    public boolean showAllRecipesInSurvival = true;

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

    /** 「查询合成」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 R。
     *  @PrefixText 复制自 recipeViewerEnabled 的信息行（原信息行保留在
     *  「实用功能」类别，这里为同一行文案的副本）。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public String recipeViewKey = KeybindingCodec.recipeViewDefaultRaw();

    /** 「查询用途」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 U。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.Tooltip
    public String usageViewKey = KeybindingCodec.usageViewDefaultRaw();

    // -- 「快捷键&数值」页底部的数值项（分节标题「音效与动画」由 ConfigTipsHelper 注入）----

    /** 翻页音效音量（0.0–1.0，默认 1.0 = 原生音量），可在「音乐与声音」界面调节。 */
    @ConfigEntry.Category("keybindings")
    public float pageFlipVolume = 1.0f;

    /** 配方书翻页动画时长（秒）。 */
    @ConfigEntry.Category("keybindings")
    public float pageAnimationDuration = 0.5f;

    // -- 杂项（miscellaneous 标签）--------------------------------------------

    /** 隐藏配置界面的Tips：打开时隐藏「实用功能」页面顶部的轮循提示行。 */
    @ConfigEntry.Category("miscellaneous")
    @ConfigEntry.Gui.Tooltip
    public boolean hideConfigTips = false;

    /** 隐藏暂停菜单的配置界面入口：打开时暂停菜单的 BRBE 配置按钮不再显示。默认关。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hidePauseMenuConfigEntry = false;

    // -- Inner config class ---------------------------------------------------

    public static class PageAnimation {
        public boolean pageAnimationEnabled = true;
    }

    public static class RecipeBookIsPain implements ConfigData {
        /** 主开关（留在「实用功能」页）。原 `@PrefixText`「§eRecipe Book Is Pain」黄字行
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
