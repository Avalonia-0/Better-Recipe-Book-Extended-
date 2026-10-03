package com.alonie.brbe.pin;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * RBIP 配方书标签固定管理器：有序固定列表（pin 顺序）+ 磁盘持久化。
 *
 * <p>固定身份 = 创造模式标签的注册 id（{@link Identifier}，跨会话稳定；RBIP 为每个
 * 创造标签现造的 {@code RecipeBookCategory} 是会话内的，不能持久化）。固定标签排在
 * 配方书首页（搜索标签下），与配方 pin 同一目录（{@code brbe.tabpins.json}，
 * 与 {@code brbe.pins.json} 并排；LEI pin 浮层自 2026-09-30 起改为**分存档 / 分服务器**
 * 存储，不再写 gameDir）。</p>
 *
 * <p><b>三档的固定分开存储</b>（用户 2026-10-01 定）：三档的 pin 键空间互不相同 ——
 * 创造模式物品栏档 = 创造标签 id，命名空间档 = **命名空间**（{@code minecraft} /
 * {@code farmersdelight}），数据包档 = **数据包 id**（{@code vanilla} / {@code mod/xxx} /
 * {@code file/xxx.zip}）。同一字符串在两档里含义可能不同（模组的命名空间与其自带数据包 id
 * 常常同名），所以各存一份。当前生效的那份由配置项决定（{@link #activeIds()}）。</p>
 *
 * <p>磁盘格式（{@code brbe.tabpins.json}）：</p>
 * <pre>{"tabs": ["minecraft:building_blocks"],
 *  "namespaceTabs": ["farmersdelight"],
 *  "datapackTabs": ["vanilla", "file/my_pack.zip"]}</pre>
 * <p>旧版文件是**一个裸数组**（只有普通模式）→ 读到时自动迁移到 {@code tabs}；
 * 第二档的旧键 {@code compactTabs}（历史上一度存过"代表创造标签 id / 裸命名空间"，
 * 后来又存过"数据包 id"）不直接采用，而是留给 RBIP 侧按内容分流到
 * {@code namespaceTabs} / {@code datapackTabs}（见 {@link #resolveLegacyCompact}）——
 * 分流需要包索引，只有进了世界才拿得到。读取同步（启动后首次使用），写入异步
 * （渲染线程不阻塞磁盘 I/O）。</p>
 */
public final class TabPinManager {

    private static final Gson GSON = new Gson();
    /** 磁盘文件的普通模式键；旧版整份文件即这个数组。 */
    private static final String KEY_TABS = "tabs";
    /** 磁盘文件的**命名空间**档键。 */
    private static final String KEY_NAMESPACE = "namespaceTabs";
    /** 磁盘文件的**数据包**档键。 */
    private static final String KEY_DATAPACK = "datapackTabs";
    /** 旧版（第二档只有一份时）的磁盘键，只用于迁移。 */
    private static final String KEY_COMPACT_LEGACY = "compactTabs";

    private static Path path;
    /** 创造模式物品栏档（每个创造标签一个配方书标签）的固定，pin 顺序。 */
    private static List<String> pinnedIds = new ArrayList<>();
    /** 命名空间档（每个命名空间一个标签）的固定，pin 顺序。 */
    private static List<String> namespacePinnedIds = new ArrayList<>();
    /** 数据包档（每个来源数据包一个标签）的固定，pin 顺序。 */
    private static List<String> datapackPinnedIds = new ArrayList<>();
    /** 待分流的旧第二档键（读到旧格式时填充，进世界后由 RBIP 调用
     *  {@link #resolveLegacyCompact} 处理）。 */
    private static List<String> legacyCompactIds = new ArrayList<>();
    private static boolean loaded;

    private TabPinManager() {}

    /** Point the manager at the game directory (same level as the query pins).
     *  Called from {@code BetterRecipeBook.init()}; the file is read lazily. */
    public static void init(Path gameDir) {
        path = gameDir.resolve("brbe.tabpins.json");
        loaded = false;
    }

    /** 当前生效档位对应的固定列表（配置不可用时按创造模式物品栏档）。 */
    private static List<String> activeIds() {
        if (BetterRecipeBook.config != null) {
            if (BetterRecipeBook.config.rbip.namespaceModeEnabled()) return namespacePinnedIds;
            if (BetterRecipeBook.config.rbip.datapackModeEnabled()) return datapackPinnedIds;
        }
        return pinnedIds;
    }

    private static void ensureLoaded() {
        if (loaded || path == null) return;
        loaded = true;
        try {
            if (!Files.exists(path)) return;
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonArray()) {
                // 旧格式：整份文件就是一个数组，只有普通模式 —— 直接迁移过来
                pinnedIds = readIds(root);
            } else if (root != null && root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                pinnedIds = readIds(obj.get(KEY_TABS));
                if (obj.has(KEY_NAMESPACE) || obj.has(KEY_DATAPACK)) {
                    namespacePinnedIds = readIds(obj.get(KEY_NAMESPACE));
                    datapackPinnedIds = readIds(obj.get(KEY_DATAPACK));
                } else {
                    // 第二档只有一份（旧格式）：内容可能是命名空间键也可能是包 id，
                    // 分流要等包索引可用（进世界后），先原样留着。
                    legacyCompactIds = readIds(obj.get(KEY_COMPACT_LEGACY));
                }
            }
        } catch (IOException | RuntimeException e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to read tab pins file: {}", e.toString());
        }
    }

    private static List<String> readIds(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return out;
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && item.isJsonPrimitive()) out.add(item.getAsString());
        }
        return out;
    }

    /** Whether the creative tab is pinned **in the current mode**. */
    public static boolean isPinned(Identifier tabId) {
        return tabId != null && isPinnedKey(tabId.toString());
    }

    /**
     * Whether an arbitrary **pin key** is pinned in the currently effective mode.
     *
     * <p>键空间随档位走：创造模式物品栏档 = 创造标签 id（{@code ns:path}）；
     * 命名空间档 = 命名空间（{@code ns}，不含冒号）；数据包档 = 数据包 id
     * （{@code vanilla} / {@code mod/xxx} / {@code file/xxx.zip}，联机降级后这一档不生效）。
     * 见 {@code RecipeBookIsPain.extendedPinKey}。</p>
     */
    public static boolean isPinnedKey(String key) {
        ensureLoaded();
        return key != null && activeIds().contains(key);
    }

    /** Toggle the creative tab's pin **in the current mode** (new pins append to the
     *  end = pin order); returns the new pinned state. */
    public static boolean toggle(Identifier tabId) {
        return tabId != null && toggleKey(tabId.toString());
    }

    /** Toggle an arbitrary pin key **in the current mode** (see {@link #isPinnedKey});
     *  returns the new pinned state. */
    public static boolean toggleKey(String key) {
        ensureLoaded();
        if (key == null) return false;
        List<String> ids = activeIds();
        boolean pinned = ids.contains(key);
        if (pinned) {
            ids.remove(key);
        } else {
            ids.add(key);
        }
        save();
        return !pinned;
    }

    /** Pinned creative-tab ids **in the current mode**, in pin order. */
    public static List<String> pinnedIds() {
        ensureLoaded();
        return List.copyOf(activeIds());
    }

    /** The pinned {@link CreativeModeTab}s **in the current mode** in pin order;
     *  unknown / unregistered ids are skipped (mod removed, etc.). */
    public static List<CreativeModeTab> pinnedTabs() {
        ensureLoaded();
        List<CreativeModeTab> out = new ArrayList<>();
        for (String id : activeIds()) {
            Identifier key = Identifier.tryParse(id);
            if (key == null) continue;
            try {
                BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(key)
                        .ifPresent(out::add);
            } catch (RuntimeException e) {
                // registry not ready — skip
            }
        }
        return out;
    }

    /**
     * 旧第二档 pin 列表的**一次性分流**（2026-10-01 拆成命名空间 / 数据包两档）：
     * 逐个键交给 {@code packKeyTest} 判定——是数据包 id 就走 {@code datapackMapper}
     * 进数据包档，否则走 {@code namespaceMapper} 进命名空间档；两个 mapper 返回 {@code null}
     * 表示丢弃。重复键只保留第一次出现的位置顺序。
     *
     * @return 被改写 / 丢弃的键数（0 = 无需保存）
     */
    public static int resolveLegacyCompact(java.util.function.Predicate<String> packKeyTest,
                                           java.util.function.UnaryOperator<String> namespaceMapper,
                                           java.util.function.UnaryOperator<String> datapackMapper) {
        ensureLoaded();
        if (legacyCompactIds.isEmpty()) return 0;
        List<String> legacy = legacyCompactIds;
        legacyCompactIds = new ArrayList<>();
        int changed = legacy.size();
        for (String key : legacy) {
            if (key == null) continue;
            boolean isPack = packKeyTest != null && packKeyTest.test(key);
            String mapped = isPack
                    ? (datapackMapper == null ? null : datapackMapper.apply(key))
                    : (namespaceMapper == null ? null : namespaceMapper.apply(key));
            if (mapped == null || mapped.isBlank()) continue;
            List<String> target = isPack ? datapackPinnedIds : namespacePinnedIds;
            if (!target.contains(mapped)) target.add(mapped);
        }
        save();
        return changed;
    }

    /** 是否还有待分流的旧第二档键（迁移尚未执行）。 */
    public static boolean hasLegacyCompact() {
        ensureLoaded();
        return !legacyCompactIds.isEmpty();
    }

    /** 清空**三档**的全部 RBIP 标签固定（{@code /brbe clear rbippin}）。返回清理前的总数。 */
    public static int clearAll() {
        ensureLoaded();
        int cleared = pinnedIds.size() + namespacePinnedIds.size() + datapackPinnedIds.size();
        pinnedIds.clear();
        namespacePinnedIds.clear();
        datapackPinnedIds.clear();
        legacyCompactIds.clear();
        save();
        return cleared;
    }

    private static void save() {
        if (path == null) return;
        JsonObject root = new JsonObject();
        root.add(KEY_TABS, toArray(pinnedIds));
        root.add(KEY_NAMESPACE, toArray(namespacePinnedIds));
        root.add(KEY_DATAPACK, toArray(datapackPinnedIds));
        String json = GSON.toJson(root);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                BetterRecipeBook.LOGGER.warn("[BRBE] Failed to write tab pins file: {}", e.toString());
            }
        });
    }

    private static JsonArray toArray(List<String> ids) {
        JsonArray array = new JsonArray();
        for (String id : ids) array.add(id);
        return array;
    }
}
