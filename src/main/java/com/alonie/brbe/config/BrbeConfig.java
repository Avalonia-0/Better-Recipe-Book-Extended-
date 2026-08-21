package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Root configuration for Better Recipe Book Extended.
 *
 * <p>Sub-configs ({@code NewRecipes}, {@code InstantCraft},
 * {@code AlternativeRecipes}, {@code Scrolling}) are standalone classes in the
 * same package — same structure as the 1.21.1 branch.  Only
 * {@code RecipeBookIsPain} is nested here (matches 1.21.1's {@code Config}).</p>
 */
@Config(name = "zzzbrbe")
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

    /** 「固定」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 F。 */
    @ConfigEntry.Gui.Tooltip
    public String pinKey = KeybindingCodec.PIN_DEFAULT_RAW;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.PrefixText
    public boolean recipeViewerEnabled = true;

    /** 隐藏无配方书工作站所属的查询对象：开启后，查询结果中所有工作站都没有配方书体系的
     *  对象被隐藏；若对象还包含有配方书体系的工作站则保留对象本身，仅 tooltip 隐藏非法
     *  工作站图标。默认关闭。 */
    public boolean hideNoRecipeBookStationObjects = false;

    /** 「查询合成」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 R。 */
    @ConfigEntry.Gui.Tooltip
    public String recipeViewKey = KeybindingCodec.recipeViewDefaultRaw();

    /** 「查询用途」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 U。 */
    @ConfigEntry.Gui.Tooltip
    public String usageViewKey = KeybindingCodec.usageViewDefaultRaw();

    @ConfigEntry.Gui.TransitiveObject
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    @ConfigEntry.Gui.TransitiveObject
    public InstantCraft instantCraft = new InstantCraft();

    // -- 界面设置（ui 标签）----------------------------------------------------

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

    /** 翻页音效音量（0.0–1.0，默认 1.0 = 原生音量），可在「音乐与声音」界面调节。 */
    @ConfigEntry.Category("ui")
    public float pageFlipVolume = 1.0f;

    /** 配方书翻页动画时长（秒）。 */
    @ConfigEntry.Category("ui")
    public float pageAnimationDuration = 0.5f;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean settingsButton = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBook = true;

    // -- 配方设置（recipeSettings 标签）----------------------------------------

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    public boolean showAllRecipesInSurvival = true;

    @ConfigEntry.Category("recipeSettings")
    public boolean hideIncompatibleMark = false;

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
    public NewRecipes newRecipes = new NewRecipes();

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.TransitiveObject
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    // -- Inner config class ---------------------------------------------------

    public static class PageAnimation {
        public boolean pageAnimationEnabled = true;
    }

    public static class RecipeBookIsPain implements ConfigData {
        @ConfigEntry.Gui.PrefixText
        @ConfigEntry.Gui.Tooltip
        public boolean enableRecipeBookIsPain = true;

        public boolean enableTabPage = true;
    }

    @Override
    public void validatePostLoad() {
        this.expandedRecipeBook = false;
    }
}
