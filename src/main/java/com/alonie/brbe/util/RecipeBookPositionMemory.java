package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Memory of where the player left each recipe book: which tab (by its index in
 * the book's tab list) and which page, plus the active search text.  Keyed by
 * the book kind ("crafting" for the vanilla recipe book, "brewing" /
 * "smithing" for the BRBE self-built books).
 *
 * <p>Persisted per save / per server, next to the pin overlays and query
 * windows ({@code <world>/brbe/positions.json} in single player,
 * {@code <gameDir>/brbe/servers/<key>/positions.json} on a server) through
 * {@link WorldScopedStore}: leaving a world keeps its browsing positions with
 * it, and entering another one must not inherit them.  Writes only happen when
 * a position actually changed, and at most one write is in flight at a time
 * (the render hook calls {@link #save} every frame).</p>
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

    /** 作用域内的文件名。 */
    private static final String FILE_NAME = "positions.json";
    private static final Gson GSON = new Gson();

    private static final Map<String, Map<Integer, Pos>> positions = new HashMap<>();
    private static final Map<String, Integer> activeTabs = new HashMap<>();

    /** 当前作用域内的文件；null = 无作用域（不读不写）。 */
    private static Path file;
    private static boolean registered;
    private static boolean dirty;
    private static boolean writing;
    private static final Object WRITE_LOCK = new Object();

    /**
     * 记录指定标签页的浏览位置，并标记该书最近激活的标签。
     * 位置没有变化时不会触发磁盘写入（本方法每帧都会被渲染钩子调用）。
     *
     * @param tabIndex 标签在标签列表中的下标（-1 忽略）
     * @param page     0-indexed 页码
     * @param tabPage  RBIP 创造标签滚动页（无则为 -1）
     * @param search   当前搜索词
     */
    public static void save(String book, int tabIndex, int page, int tabPage, String search) {
        if (book == null || tabIndex < 0 || page < 0) return;
        attach();
        String s = search != null ? search : "";
        // basePage：空搜索时跟随当前页；搜索状态下保留该标签上次空搜索时的页码
        int basePage = page;
        if (!s.isEmpty()) {
            Pos old = peek(book, tabIndex);
            if (old != null) basePage = old.basePage();
        }
        Pos next = new Pos(page, tabPage, s, basePage);
        Map<Integer, Pos> tabs = positions.computeIfAbsent(book, k -> new HashMap<>());
        Pos previous = tabs.put(tabIndex, next);
        Integer previousActive = activeTabs.put(book, tabIndex);
        if (next.equals(previous) && previousActive != null && previousActive == tabIndex) {
            return;
        }
        dirty = true;
        flushAsync();
    }

    /** 指定标签页的浏览位置；该标签从未被记录过时返回 null。 */
    public static Pos load(String book, int tabIndex) {
        if (book == null || tabIndex < 0) return null;
        attach();
        return peek(book, tabIndex);
    }

    /** 该书最近激活的标签下标；无记录返回 -1。 */
    public static int activeTabIndex(String book) {
        if (book == null) return -1;
        attach();
        Integer index = activeTabs.get(book);
        return index != null ? index : -1;
    }

    public static void clear(String book) {
        attach();
        if (positions.remove(book) == null && activeTabs.remove(book) == null) return;
        dirty = true;
        flushAsync();
    }

    private static Pos peek(String book, int tabIndex) {
        Map<Integer, Pos> tabs = positions.get(book);
        return tabs == null ? null : tabs.get(tabIndex);
    }

    /** 注册作用域监听并解析当前作用域（幂等；位置存储第一次被访问时调用）。 */
    private static void attach() {
        if (!registered) {
            registered = true;
            WorldScopedStore.addListener(RecipeBookPositionMemory::onScopeChanged);
        }
        WorldScopedStore.refresh();
    }

    /**
     * 作用域切换：旧作用域的数据先落盘（文件路径仍记在 {@link #file}），再清空内存、
     * 载入新作用域。不清空的话，上一个存档的浏览位置会泄漏到下一个存档 / 服务器。
     */
    private static void onScopeChanged(Path dir) {
        if (dirty && file != null) {
            // 切换前同步落盘：文件很小，且必须赶在清空内存之前完成
            writeNow(file, snapshot());
            dirty = false;
        }
        positions.clear();
        activeTabs.clear();
        file = dir == null ? null : dir.resolve(FILE_NAME);
        loadFromDisk();
    }

    private static void loadFromDisk() {
        if (file == null || !Files.exists(file)) return;
        try {
            FileData data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileData.class);
            if (data == null) return;
            if (data.positions != null) {
                for (Map.Entry<String, Map<String, PosData>> book : data.positions.entrySet()) {
                    if (book.getKey() == null || book.getValue() == null) continue;
                    Map<Integer, Pos> tabs = new HashMap<>();
                    for (Map.Entry<String, PosData> tab : book.getValue().entrySet()) {
                        PosData stored = tab.getValue();
                        if (stored == null) continue;
                        try {
                            tabs.put(Integer.parseInt(tab.getKey()), new Pos(stored.page, stored.tabPage,
                                    stored.search != null ? stored.search : "", stored.basePage));
                        } catch (NumberFormatException ignored) {
                            // 手改过的文件里出现非数字标签下标：跳过该条
                        }
                    }
                    positions.put(book.getKey(), tabs);
                }
            }
            if (data.activeTabs != null) {
                for (Map.Entry<String, Integer> active : data.activeTabs.entrySet()) {
                    if (active.getKey() != null && active.getValue() != null) {
                        activeTabs.put(active.getKey(), active.getValue());
                    }
                }
            }
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to read recipe book positions: {}", e.getMessage());
        }
    }

    /** 自上次写入后又有变化时补写一次（同一时刻最多一个写入在途）。 */
    private static void flushAsync() {
        Path target;
        FileData data;
        synchronized (WRITE_LOCK) {
            if (writing || !dirty || file == null) return;
            writing = true;
            dirty = false;
            target = file;
            data = snapshot();
        }
        CompletableFuture.runAsync(() -> {
            writeNow(target, data);
            boolean again;
            synchronized (WRITE_LOCK) {
                writing = false;
                again = dirty;
            }
            if (again) flushAsync();
        });
    }

    private static void writeNow(Path target, FileData data) {
        if (target == null) return;
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to write recipe book positions: {}", e.getMessage());
        }
    }

    private static FileData snapshot() {
        FileData data = new FileData();
        for (Map.Entry<String, Map<Integer, Pos>> book : positions.entrySet()) {
            Map<String, PosData> tabs = new LinkedHashMap<>();
            for (Map.Entry<Integer, Pos> tab : book.getValue().entrySet()) {
                Pos pos = tab.getValue();
                PosData stored = new PosData();
                stored.page = pos.page();
                stored.tabPage = pos.tabPage();
                stored.basePage = pos.basePage();
                stored.search = pos.search();
                tabs.put(String.valueOf(tab.getKey()), stored);
            }
            data.positions.put(book.getKey(), tabs);
        }
        data.activeTabs.putAll(activeTabs);
        return data;
    }

    /** 磁盘格式（字段名即 JSON 键；标签下标序列化为字符串，Gson 对 map 键的处理一致）。 */
    private static final class FileData {
        Map<String, Map<String, PosData>> positions = new LinkedHashMap<>();
        Map<String, Integer> activeTabs = new LinkedHashMap<>();
    }

    private static final class PosData {
        int page;
        int tabPage;
        int basePage;
        String search;
    }
}
