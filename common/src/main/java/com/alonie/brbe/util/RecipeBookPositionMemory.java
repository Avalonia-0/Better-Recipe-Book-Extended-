package com.alonie.brbe.util;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory memory of where the player left each recipe book: which tab (by
 * its {@link net.minecraft.client.RecipeBookCategories} name — stable across
 * RBIP tab reordering) and which page, plus the active search text.  Keyed by
 * the book kind ("crafting" for the vanilla recipe book, "brewing" /
 * "smithing" for the BRBE self-built books).  Not persisted to disk.
 *
 * <p>每个标签页单独保存自己的浏览位置（{@link #save} 以类别名称为子键），
 * 切换标签再切回来会恢复该标签上一次的页码；同时记录该书最近激活的标签
 * （{@link #activeTabCategory}），重新打开配方书时恢复该标签及其位置。</p>
 *
 * <p><b>按类别名称（而非下标）索引</b>：RBIP 会把标签重排（固定标签置顶、
 * 分页隐藏/显示），下标在保存/恢复之间会漂移导致"恢复错标签"。类别名是
 * 稳定的（{@code RecipeBookCategories} 是 IExtensibleEnum），恢复时按类别
 * 精确定位标签。</p>
 *
 * <p>每个标签额外保存一个"空搜索时的页码"（{@link Pos#basePage()}）：
 * 空搜索状态下它持续跟随当前页，搜索状态下保持不变；搜索词清空时用它恢复
 * 搜索前的浏览页码。</p>
 */
public final class RecipeBookPositionMemory {

    private RecipeBookPositionMemory() {}

    /**
     * 一个标签页的浏览位置：当前页码、RBIP 创造标签滚动页、搜索词、
     * 空搜索时的浏览页码（搜索词清空后恢复用）。
     */
    public record Pos(int page, int tabPage, String search, int basePage) {}

    private static final Map<String, Map<String, Pos>> positions = new HashMap<>();
    private static final Map<String, String> activeTabs = new HashMap<>();

    /**
     * 记录指定标签页的浏览位置，并标记该书最近激活的标签。
     *
     * @param tabCategory 标签的 {@code RecipeBookCategories.name()}（null/空忽略）
     * @param page        0-indexed 页码
     * @param tabPage     RBIP 创造标签滚动页（无则为 -1）
     * @param search      当前搜索词
     */
    public static void save(String book, String tabCategory, int page, int tabPage, String search) {
        if (book == null || tabCategory == null || tabCategory.isEmpty() || page < 0) return;
        String s = search != null ? search : "";
        // basePage：空搜索时跟随当前页；搜索状态下保留该标签上次空搜索时的页码
        int basePage = page;
        if (!s.isEmpty()) {
            Pos old = load(book, tabCategory);
            if (old != null) basePage = old.basePage();
        }
        positions.computeIfAbsent(book, k -> new HashMap<>()).put(tabCategory,
                new Pos(page, tabPage, s, basePage));
        activeTabs.put(book, tabCategory);
    }

    /** 指定标签页的浏览位置；该标签从未被记录过时返回 null。 */
    public static Pos load(String book, String tabCategory) {
        if (book == null || tabCategory == null) return null;
        Map<String, Pos> tabs = positions.get(book);
        return tabs == null ? null : tabs.get(tabCategory);
    }

    /** 该书最近激活的标签类别名；无记录返回 null。 */
    public static String activeTabCategory(String book) {
        if (book == null) return null;
        return activeTabs.get(book);
    }

    public static void clear(String book) {
        positions.remove(book);
        activeTabs.remove(book);
    }
}
