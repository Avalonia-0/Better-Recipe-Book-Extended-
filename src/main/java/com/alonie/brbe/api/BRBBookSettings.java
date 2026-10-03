package com.alonie.brbe.api;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.BRBHelper;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import com.alonie.brbe.util.BrbeLogger;

public class BRBBookSettings {
    private static final Map<Identifier, TypeSettings> states = new HashMap<>();


    public static void registerBook(BRBHelper.Book book) {
        if (book == null) return;
        BrbeLogger.log("BRBE", "Registering book {}", book.Identifier);
        states.put(book.Identifier, new TypeSettings(false, false));
    }

    public static boolean isOpen(BRBHelper.Book book) {
        if (book == null) return false;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return false;
        return settings.open;
    }

    public static void setOpen(BRBHelper.Book book, boolean bl) {
        if (book == null) return;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return;
        settings.open = bl;
    }

    /**
     * 自研书（锻造台 / 酿造台）的过滤状态 —— **有效**状态，不是原始按钮状态。
     *
     * <p>开启「优化原版配方过滤器」（{@code partialCraftingEnabled}，用户 2026-09-27 诉求）时
     * 恒为 {@code false}：过滤按钮隐藏（见 {@code GenericRecipeBookComponent#initVisuals}）、
     * 配方全显示，优先级只由「可合成 → 残缺 → 其余」排序表达 —— 与原版书同一套语义
     * （原版侧见 {@code mixins/DisableCraftableFilter} + {@code pipeline/RecipeBookComponentMixin}
     * 的 Stage 4）。</p>
     */
    public static boolean isFiltering(BRBHelper.Book book) {
        if (book == null) return false;
        if (partialFilterMode()) return false;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return false;
        return settings.filtering;
    }

    /** 「优化原版配方过滤器」是否开启（配置项 {@code partialCraftingEnabled}）：自研书据此
     *  隐藏过滤按钮、加宽搜索栏并恒做「可合成置顶」排序。 */
    public static boolean partialFilterMode() {
        return BetterRecipeBook.config != null && BetterRecipeBook.config.partialCraftingEnabled;
    }

    public static void setFiltering(BRBHelper.Book book, boolean bl) {
        if (book == null) return;
        TypeSettings settings = states.get(book.Identifier);
        if (settings == null) return;
        settings.filtering = bl;
    }


    static class TypeSettings {
        boolean open;
        boolean filtering;

        public TypeSettings(boolean bl, boolean bl2) {
            this.open = bl;
            this.filtering = bl2;
        }
    }
}
