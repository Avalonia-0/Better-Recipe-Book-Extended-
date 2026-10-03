package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;

public final class RecipeBookIsPainExtendedConfig {

    private RecipeBookIsPainExtendedConfig() {}

    public static boolean enabled() {
        if (BetterRecipeBook.config == null) return true;
        return BetterRecipeBook.config.rbip.enableRecipeBookIsPain;
    }

    public static int bottomNumber() {
        if (BetterRecipeBook.config == null) return 16;
        return BetterRecipeBook.config.rbip.enableTabPage ? 16 : 6;
    }

    /** 「标签模式」（{@code [rbip] tabMode}）：每档的完整语义见
     *  {@code BrbeConfig.RecipeBookIsPain.TabMode}。配置不可用时按「创造模式物品栏」处理。 */
    public static boolean isDatapackMode() {
        if (BetterRecipeBook.config == null) return false;
        return BetterRecipeBook.config.rbip.datapackModeEnabled();
    }

    /** 当前是不是「命名空间」档（联机时数据包档降级到这一档，见
     *  {@code BrbeConfig.RecipeBookIsPain.effectiveTabMode()}）。 */
    public static boolean isNamespaceMode() {
        if (BetterRecipeBook.config == null) return false;
        return BetterRecipeBook.config.rbip.namespaceModeEnabled();
    }

    /** **实际生效**的档位名（{@code CREATIVE_TABS} / {@code NAMESPACE} / {@code DATAPACK}）；
     *  配置不可用时按默认档。用于检测"档位变了" —— 联机进出的降级/恢复也算变化。 */
    public static String effectiveTabMode() {
        if (BetterRecipeBook.config == null) return "CREATIVE_TABS";
        return BetterRecipeBook.config.rbip.effectiveTabMode().name();
    }

    /** 「标签布局」指纹（生效档位 + RBIP 开关 + 每页标签数）。在配置界面里改这三样中的
     *  任何一样，标签栏都必须在 **vanilla initVisuals 造标签按钮之前**重算 —— 见
     *  {@code RecipeBookWidgetMixin.rbip$rebuildTabsOnInit}。无副作用，可随时调用。 */
    public static String tabLayoutKey() {
        return effectiveTabMode() + "|" + enabled() + "|" + bottomNumber();
    }

    /** @deprecated All features are now core; always returns true. */
    @Deprecated
    public boolean extendedFeatures() { return true; }

    public static RecipeBookIsPainExtendedConfig get() { return Holder.INSTANCE; }
    private static final class Holder { static final RecipeBookIsPainExtendedConfig INSTANCE = new RecipeBookIsPainExtendedConfig(); }

    private static boolean lastEnabled = true;
    private static int lastBottomNumber = 16;
    /** 上次看到的**生效档位**（联机降级/恢复也会让这个值变），见 {@link #effectiveTabMode()}。 */
    private static String lastTabMode = "CREATIVE_TABS";

    public static boolean reloadIfChanged() {
        boolean changed = false;
        boolean current = enabled();
        int currentBottom = bottomNumber();
        String currentTabMode = effectiveTabMode();
        if (current != lastEnabled || currentBottom != lastBottomNumber
                || !currentTabMode.equals(lastTabMode)) {
            lastEnabled = current;
            lastBottomNumber = currentBottom;
            lastTabMode = currentTabMode;
            changed = true;
        }
        return changed;
    }
}
