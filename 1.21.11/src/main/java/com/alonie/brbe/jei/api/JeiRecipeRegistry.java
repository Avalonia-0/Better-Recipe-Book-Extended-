package com.alonie.brbe.jei.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 无头 JEI 轻量桥：向外部（BRBE 主 mod）暴露嵌入式 JEI 运行时的配方条目。
 *
 * <p>条目由 {@code PluginRecipeIndexer} 在每次收集后写入（mod 插件配方 +
 * 原版 anvil/brewing/grindstone 运行时配方）。外部 mod 在 JOIN/重建时读取
 * {@link #entriesFor} 转进自己的查询引擎；渲染委托 {@link JeiPopupRenderer}。</p>
 *
 * <p>条目是不可变值对象（{@link Entry}），不含任何 mezz.jei 内部引用——
 * 外部只需 {@code com.alonie.zheadlessjei} 编译依赖即可。1.21.11 版：
 * 类型 uid 用 {@link Identifier}（1.21.5+ 命名）。</p>
 */
public final class JeiRecipeRegistry {

    private JeiRecipeRegistry() {}

    /** A collected JEI recipe: its JEI type uid, the raw recipe object, its
     *  extracted item inputs/outputs and (for mod recipes) the declared slot
     *  layout. */
    public record Entry(Identifier typeUid, Object recipe,
                        List<ItemStack> inputs, List<ItemStack> outputs,
                        List<Slot> slots, int layoutWidth, int layoutHeight) {
        /** Convenience: entry without slot-layout data (interface-accessed
         *  vanilla JEI recipes). */
        public Entry(Identifier typeUid, Object recipe,
                     List<ItemStack> inputs, List<ItemStack> outputs) {
            this(typeUid, recipe, inputs, outputs, null, 0, 0);
        }

        /** One slot of the recipe's native layout (role = JEI
         *  RecipeIngredientRole ordinal). */
        public record Slot(int x, int y, int role, List<ItemStack> stacks) {}
    }

    private static final Map<Identifier, List<Entry>> ENTRIES = new HashMap<>();
    private static final Map<Identifier, List<ItemStack>> STATIONS = new HashMap<>();
    private static final Map<Identifier, String> TITLES = new HashMap<>();

    /** Merge entries/stations into the registry (per-uid replace; other uids
     *  are preserved).  The indexer calls this for mod plugins and vanilla
     *  runtime categories as separate passes — a whole-registry replace would
     *  drop the earlier pass's data. */
    public static synchronized void putAll(Map<Identifier, List<Entry>> entries,
                                           Map<Identifier, List<ItemStack>> stations,
                                           Map<Identifier, String> titles) {
        if (entries != null) {
            for (Map.Entry<Identifier, List<Entry>> e : entries.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    ENTRIES.remove(e.getKey());
                } else {
                    ENTRIES.put(e.getKey(), e.getValue());
                }
            }
        }
        if (stations != null) {
            for (Map.Entry<Identifier, List<ItemStack>> e : stations.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    STATIONS.remove(e.getKey());
                } else {
                    STATIONS.put(e.getKey(), e.getValue());
                }
            }
        }
        if (titles != null) {
            for (Map.Entry<Identifier, String> e : titles.entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank()) {
                    TITLES.remove(e.getKey());
                } else {
                    TITLES.put(e.getKey(), e.getValue());
                }
            }
        }
    }

    /** Display title of a type uid (raw string; consumer wraps in Component). */
    public static synchronized String titleFor(Identifier typeUid) {
        return TITLES.get(typeUid);
    }

    /** All JEI entries of a type uid (unmodifiable view). */
    public static synchronized List<Entry> entriesFor(Identifier typeUid) {
        List<Entry> entries = ENTRIES.get(typeUid);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /** All currently registered JEI type uids. */
    public static synchronized List<Identifier> typeIds() {
        return new ArrayList<>(ENTRIES.keySet());
    }

    /** Workstation items of a type uid. */
    public static synchronized List<ItemStack> stationsFor(Identifier typeUid) {
        return STATIONS.getOrDefault(typeUid, List.of());
    }

    /** Whether the registry has any entry for {@code typeUid}. */
    public static synchronized boolean hasType(Identifier typeUid) {
        List<Entry> entries = ENTRIES.get(typeUid);
        return entries != null && !entries.isEmpty();
    }

    /** Drop all entries (disconnect / shutdown). */
    public static synchronized void clear() {
        ENTRIES.clear();
        STATIONS.clear();
    }
}
