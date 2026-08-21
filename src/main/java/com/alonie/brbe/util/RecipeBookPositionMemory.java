package com.alonie.brbe.util;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory memory of where the player left each recipe book: which tab (by
 * its index in the book's tab list) and which page, plus the active search
 * text.  Keyed by the book kind ("crafting" for the vanilla recipe book,
 * "brewing" / "smithing" for the BRBE self-built books).  Not persisted to
 * disk.
 *
 * <p>每个标签页单独保存自己的浏览位置（{@link #save} 以 tabIndex 为子键），
 * 切换标签再切回来会恢复该标签上一次的页码；同时记录该书最近激活的标签
 * （{@link #activeTabIndex}），重新打开配方书时恢复该标签及其位置。</p>
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

    private static final Map<String, Map<Integer, Pos>> positions = new HashMap<>();
    private static final Map<String, Integer> activeTabs = new HashMap<>();

    /**
     * 记录指定标签页的浏览位置，并标记该书最近激活的标签。
     *
     * @param tabIndex 标签在标签列表中的下标（-1 忽略）
     * @param page     0-indexed 页码
     * @param tabPage  RBIP 创造标签滚动页（无则为 -1）
     * @param search   当前搜索词
     */
    public static void save(String book, int tabIndex, int page, int tabPage, String search) {
        if (book == null || tabIndex < 0 || page < 0) return;
        String s = search != null ? search : "";
        // basePage：空搜索时跟随当前页；搜索状态下保留该标签上次空搜索时的页码
        int basePage = page;
        if (!s.isEmpty()) {
            Pos old = load(book, tabIndex);
            if (old != null) basePage = old.basePage();
        }
        positions.computeIfAbsent(book, k -> new HashMap<>()).put(tabIndex,
                new Pos(page, tabPage, s, basePage));
        activeTabs.put(book, tabIndex);
    }

    /** 指定标签页的浏览位置；该标签从未被记录过时返回 null。 */
    public static Pos load(String book, int tabIndex) {
        if (book == null || tabIndex < 0) return null;
        Map<Integer, Pos> tabs = positions.get(book);
        return tabs == null ? null : tabs.get(tabIndex);
    }

    /** 该书最近激活的标签下标；无记录返回 -1。 */
    public static int activeTabIndex(String book) {
        if (book == null) return -1;
        Integer index = activeTabs.get(book);
        return index != null ? index : -1;
    }

    public static void clear(String book) {
        positions.remove(book);
        activeTabs.remove(book);
    }
}
