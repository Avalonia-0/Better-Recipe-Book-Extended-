package com.alonie.brbe.recipeviewer.engine;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.21.1 版查询引擎（RecipeHolder 索引）——对齐 1.21.11 的 RecipeViewerEngine
 * 接口语义，但数据模型用 1.21.1 的旧 Recipe API（无 RecipeDisplayEntry/SlotDisplay）。
 *
 * <p>职责：注册类别（registerType）→ 按结果物品反查配方（R）/按材料物品反查配方（U）
 * /工作站物品返回整个类别。索引构建与 UI 完全解耦。</p>
 *
 * <p>与 1.21.11 的差异：{@code RecipeDisplayEntry} 替换为 {@link RecipeHolder}
 * （1.21.1 的 RecipeManager.getAllRecipes() 即此类）；display 相关方法（
 * registerLayout/getLayout/isSynthetic 等）暂缺——由后续 UI 层的
 * PopupGeometry/PopupRenderer 1.21.1 版按需增加。</p>
 */
public final class RecipeViewerEngine {

    private RecipeViewerEngine() {}

    /** A recipe plus its already-extracted input and output item stacks.
     *  Split entries of one source recipe share the same {@code groupKey}, so
     *  usage lookups can show the recipe once instead of once per product. */
    public record IndexedRecipe(RecipeHolder<?> holder, List<ItemStack> inputs, List<ItemStack> outputs,
                                Object groupKey) {
        public IndexedRecipe(RecipeHolder<?> holder, List<ItemStack> inputs, List<ItemStack> outputs) {
            this(holder, inputs, outputs, null);
        }
    }

    /** The seven vanilla JEI recipe type ids (1.21.1 view 类别前缀保留，供
     *  UI 层判断内置/外部类别）。 */
    private static final Set<String> VANILLA_TYPES = Set.of(
            "minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing");

    private static final Map<String, RecipeTypeData> TYPES = new LinkedHashMap<>();
    private static final List<Runnable> REBUILD_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Register (or replace) a recipe type's full recipe list and its
     *  workstation block items.  Builds the OUTPUT/INPUT reverse indices. */
    public static void registerType(String uid, List<IndexedRecipe> recipes, List<ItemStack> stations) {
        if (uid == null) return;
        RecipeTypeData data = new RecipeTypeData(uid, stations);
        if (recipes != null) {
            for (IndexedRecipe recipe : recipes) {
                if (recipe == null || recipe.holder() == null) continue;
                data.addRecipe(recipe.holder(), recipe.inputs(), recipe.outputs(), recipe.groupKey());
                // 阶段二 B：holder 条目挂靠身份表（entryFor/isSynthetic 用）。
                BY_ID.put(idFor(recipe.holder()), recipe.holder());
            }
        }
        TYPES.put(uid, data);
    }

    /** Recipes of {@code uid} whose result is {@code target} (R). */
    public static List<RecipeHolder<?>> resultsFor(String uid, ItemStack target) {
        RecipeTypeData data = TYPES.get(uid);
        if (data == null || target == null || target.isEmpty()) return List.of();
        return data.resultsFor(target);
    }

    /** Recipes of {@code uid} using {@code target} as material (U).  A
     *  workstation block of this type returns the whole type (JEI semantics). */
    public static List<RecipeHolder<?>> usagesFor(String uid, ItemStack target) {
        RecipeTypeData data = TYPES.get(uid);
        if (data == null || target == null || target.isEmpty()) return List.of();
        return data.usagesFor(target);
    }

    /** Every recipe of {@code uid}, unfiltered. */
    public static List<RecipeHolder<?>> allRecipes(String uid) {
        RecipeTypeData data = TYPES.get(uid);
        return data == null ? new ArrayList<>() : new ArrayList<>(data.recipes);
    }

    /** Whether {@code target} is one of {@code uid}'s workstation blocks. */
    public static boolean isStation(String uid, ItemStack target) {
        RecipeTypeData data = TYPES.get(uid);
        return data != null && target != null && !target.isEmpty() && data.stationItems.contains(target.getItem());
    }

    /** Whether {@code uid} has anything to show for {@code target}. */
    public static boolean hasContent(String uid, ItemStack target, boolean usage) {
        List<RecipeHolder<?>> hits = usage ? usagesFor(uid, target) : resultsFor(uid, target);
        return !hits.isEmpty();
    }

    /** Drop all registered types and notify rebuild listeners. */
    public static void clear() {
        TYPES.clear();
        notifyRebuilt();
    }

    /** Drop only the vanilla recipe types (leaving mod-registered types). */
    public static void clearVanilla() {
        TYPES.keySet().removeIf(VANILLA_TYPES::contains);
        notifyRebuilt();
    }

    /** Drop one type (e.g. a no-recipe-book workstation type when the
     *  "hide no-recipe-book station objects" filter is on). */
    public static void clearType(String uid) {
        TYPES.remove(uid);
        notifyRebuilt();
    }

    public static boolean isVanillaType(String uid) {
        return VANILLA_TYPES.contains(uid);
    }

    // -- Recipe-book-station tracking (hideNoRecipeBookStationObjects) --------

    /** Workstation block items that have a recipe-book UI (vanilla
     *  recipeBook=true stations; mod stations of recipe-book-driven types are
     *  appended by the collector).  Rebuilt with every collection pass. */
    private static final Set<Item> RECIPE_BOOK_STATION_ITEMS = new LinkedHashSet<>();

    /** Recipe-book-driven mod type ids (recipeBook=true external stations). */
    private static final Set<String> RECIPE_BOOK_TYPES = new LinkedHashSet<>();

    /** Replace the recipe-book-station item set (called on every engine
     *  rebuild / JEI collection pass). */
    public static void setRecipeBookStationItems(java.util.Collection<ItemStack> stations) {
        RECIPE_BOOK_STATION_ITEMS.clear();
        if (stations != null) {
            for (ItemStack station : stations) {
                if (station != null && !station.isEmpty()) {
                    RECIPE_BOOK_STATION_ITEMS.add(station.getItem());
                }
            }
        }
    }

    /** Mark a type id as recipe-book-driven (external station registered for a
     *  type with a recipe-book UI). */
    public static void registerRecipeBookType(String uid) {
        if (uid != null) RECIPE_BOOK_TYPES.add(uid);
    }

    /** Whether {@code station} is a recipe-book-backed workstation block (the
     *  authority signal for hideNoRecipeBookStationObjects). */
    public static boolean isRecipeBookStation(ItemStack station) {
        return station != null && !station.isEmpty()
                && RECIPE_BOOK_STATION_ITEMS.contains(station.getItem());
    }

    /** Whether {@code uid} is a recipe-book-driven type. */
    public static boolean isRecipeBookType(String uid) {
        return RECIPE_BOOK_TYPES.contains(uid);
    }

    // -- JEI (headless embedded runtime) entries ------------------------------

    /** One slot of a JEI recipe's native layout (from headless-jei's
     *  {@code JeiRecipeRegistry.Entry.Slot}: x/y are layout-local px,
     *  role is the {@code RecipeIngredientRole} ordinal). */
    public record JeiSlot(int x, int y, int role, List<ItemStack> stacks) {}

    /** A recipe collected from a JEI plugin (embedded headless core or real
     *  JEI): its JEI type uid, the raw recipe object and its already-extracted
     *  item inputs/outputs.  {@code slots}/{@code layoutWidth}/{@code layoutHeight}
     *  carry the recipe's native JEI layout when available (headless-jei bridge
     *  passes them through) — the popup geometry uses them for 1:1 rendering. */
    public record JeiEntry(ResourceLocation typeUid, Object recipe,
                           List<ItemStack> inputs, List<ItemStack> outputs,
                           List<JeiSlot> slots, int layoutWidth, int layoutHeight) {
        public JeiEntry(ResourceLocation typeUid, Object recipe,
                        List<ItemStack> inputs, List<ItemStack> outputs) {
            this(typeUid, recipe, inputs, outputs, null, 0, 0);
        }
    }

    private static final Map<String, JeiTypeData> JEI_TYPES = new LinkedHashMap<>();

    // ── 阶段二 B：内建 display 等价模型（1.21.11 RecipeLayout/DisplayId 移植） ──
    // 1.21.1 无 RecipeDisplay/SlotDisplay（1.21.5+）——在 mod 内部建等价抽象，
    // 让前端可按 1.21.11 逐行移植。条目身份 = 稳定 key（RecipeHolder.id() 或
    // JEI typeUid），layout 经注册表间接存取（registerLayout/getLayout）。

    /** 条目稳定身份键（1.21.11 RecipeDisplayId 等价物）。 */
    public record RecipeDisplayId(String key) {
        @Override public String toString() { return key; }
    }

    /** 一个原生布局槽位（x/y 为布局内像素、role 为 RecipeIngredientRole
     *  ordinal、stacks 为该槽物品）。1.21.11 RecipeSlotLayout 等价物。 */
    public record RecipeSlotLayout(int x, int y, int role, List<ItemStack> stacks) {}

    /** 类别背景纹理（JEI 类别 createDrawable 声明）。nullable。 */
    public record RecipeBackground(ResourceLocation texture, int u, int v, int width, int height,
                                   int textureWidth, int textureHeight) {}

    /** 条目原生布局（宽/高/槽位/类别背景）。1.21.11 RecipeLayout 等价物；
     *  JEI 条目已自带 layoutWidth/Height、槽位在此统一挂靠注册表。 */
    public record RecipeLayout(int width, int height, List<RecipeSlotLayout> slots,
                               RecipeBackground background) {}

    private static final Map<RecipeDisplayId, RecipeHolder<?>> BY_ID = new HashMap<>();
    private static final Map<RecipeDisplayId, RecipeLayout> LAYOUTS = new HashMap<>();

    /** {@code RecipeHolder} 条目的稳定身份键（1.21.11 PinnableRecipeCollection.idFor
     *  等价物：holder.id() 的 ResourceLocation 字符串）。 */
    public static RecipeDisplayId idFor(RecipeHolder<?> holder) {
        return new RecipeDisplayId(holder == null ? "" : holder.id().toString());
    }

    /** JEI 条目身份键（typeUid 唯一——每类内以类型为身份；1.21.11 JEI 条目同）。 */
    public static RecipeDisplayId idForJei(ResourceLocation typeUid) {
        return new RecipeDisplayId("jei:" + (typeUid == null ? "" : typeUid));
    }

    /** 按身份键取条目（BY_ID；未注册返回 null）。 */
    public static RecipeHolder<?> entryFor(RecipeDisplayId id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 是否合成/虚拟条目（1.21.11 isSynthetic 等价物：无 RecipeHolder 即 JEI 条目）。 */
    public static boolean isSynthetic(RecipeDisplayId id) {
        if (id == null) return false;
        RecipeHolder<?> holder = BY_ID.get(id);
        return holder == null;
    }

    /** 注册条目原生布局（1.21.11 registerLayout 等价物；幂等替换）。 */
    public static void registerLayout(RecipeDisplayId id, RecipeLayout layout) {
        if (id != null && layout != null) {
            LAYOUTS.put(id, layout);
        }
    }

    /** 已注册的条目布局，或 null。 */
    public static RecipeLayout getLayout(RecipeDisplayId id) {
        return id == null ? null : LAYOUTS.get(id);
    }

    /** Register (or replace) a JEI type's recipes and its workstation items
     *  (collected from JEI plugins / vanilla JEI recipes). */
    public static void registerJeiType(String uid, List<JeiEntry> entries, List<ItemStack> stations) {
        if (uid == null) return;
        JeiTypeData data = new JeiTypeData(uid, stations);
        if (entries != null) {
            for (JeiEntry entry : entries) {
                if (entry != null && entry.recipe() != null) {
                    data.addEntry(entry);
                    // 阶段二 B：JEI 条目挂靠身份表 + 注册原生布局（槽位已有）。
                    RecipeDisplayId id = idForJei(entry.typeUid());
                    if (entry.slots() != null && !entry.slots().isEmpty()
                            && entry.layoutWidth() > 0 && entry.layoutHeight() > 0) {
                        List<RecipeSlotLayout> slotLayouts = new ArrayList<>();
                        for (JeiSlot slot : entry.slots()) {
                            slotLayouts.add(new RecipeSlotLayout(
                                    slot.x(), slot.y(), slot.role(),
                                    slot.stacks() == null ? List.of() : slot.stacks()));
                        }
                        registerLayout(id, new RecipeLayout(
                                entry.layoutWidth(), entry.layoutHeight(), slotLayouts, null));
                    }
                }
            }
        }
        JEI_TYPES.put(uid, data);
    }

    /** Drop only the JEI-registered types (mod recipes re-collected on the
     *  next join / rebuild). */
    public static void clearJei() {
        JEI_TYPES.clear();
    }

    /** JEI recipes of {@code uid} whose output is {@code target} (R). */
    public static List<JeiEntry> jeiResultsFor(String uid, ItemStack target) {
        JeiTypeData data = JEI_TYPES.get(uid);
        if (data == null || target == null || target.isEmpty()) return List.of();
        return data.resultsFor(target);
    }

    /** JEI recipes of {@code uid} using {@code target} as material (U).  A
     *  workstation item returns the whole JEI type. */
    public static List<JeiEntry> jeiUsagesFor(String uid, ItemStack target) {
        JeiTypeData data = JEI_TYPES.get(uid);
        if (data == null || target == null || target.isEmpty()) return List.of();
        return data.usagesFor(target);
    }

    /** Every JEI recipe of {@code uid}, unfiltered. */
    public static List<JeiEntry> allJeiRecipes(String uid) {
        JeiTypeData data = JEI_TYPES.get(uid);
        return data == null ? new ArrayList<>() : new ArrayList<>(data.entries);
    }

    /** Whether {@code uid} has JEI content for {@code target}. */
    public static boolean hasJeiContent(String uid, ItemStack target, boolean usage) {
        return !(usage ? jeiUsagesFor(uid, target) : jeiResultsFor(uid, target)).isEmpty();
    }

    /** Callback fired whenever the engine content is rebuilt. */
    public static void addRebuildListener(Runnable listener) {
        REBUILD_LISTENERS.add(listener);
    }

    /** Publishes a rebuild notification (index layer calls after a full
     *  reconstruction; keeps the listener list here). */
    public static void notifyRebuiltPublic() {
        notifyRebuilt();
    }

    private static void notifyRebuilt() {
        for (Runnable listener : REBUILD_LISTENERS) {
            try {
                listener.run();
            } catch (Exception e) {
                // a broken listener must not break the rebuild
            }
        }
    }

    private static final class RecipeTypeData {
        final String uid;
        final List<RecipeHolder<?>> recipes = new ArrayList<>();
        final Set<Item> stationItems = new LinkedHashSet<>();
        final Map<Item, List<RecipeHolder<?>>> outputIndex = new HashMap<>();
        /** input item → (recipe group → one representative entry). */
        final Map<Item, Map<Object, RecipeHolder<?>>> inputIndex = new HashMap<>();
        final Map<RecipeHolder<?>, Object> entryGroups = new HashMap<>();

        RecipeTypeData(String uid, List<ItemStack> stations) {
            this.uid = uid;
            if (stations != null) {
                for (ItemStack station : stations) {
                    if (station != null && !station.isEmpty()) {
                        stationItems.add(station.getItem());
                    }
                }
            }
        }

        void addRecipe(RecipeHolder<?> holder, List<ItemStack> inputs, List<ItemStack> outputs, Object groupKey) {
            recipes.add(holder);
            entryGroups.put(holder, groupKey);
            if (outputs != null) {
                for (ItemStack output : outputs) {
                    if (output != null && !output.isEmpty()) {
                        outputIndex.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(holder);
                    }
                }
            }
            if (inputs != null) {
                for (ItemStack input : inputs) {
                    if (input != null && !input.isEmpty()) {
                        Object key = groupKey != null ? groupKey : holder;
                        inputIndex.computeIfAbsent(input.getItem(), k -> new HashMap<>())
                                .putIfAbsent(key, holder);
                    }
                }
            }
        }

        List<RecipeHolder<?>> resultsFor(ItemStack target) {
            List<RecipeHolder<?>> hits = outputIndex.get(target.getItem());
            return hits == null ? new ArrayList<>() : new ArrayList<>(hits);
        }

        List<RecipeHolder<?>> usagesFor(ItemStack target) {
            if (stationItems.contains(target.getItem())) return distinctRecipes();
            Map<Object, RecipeHolder<?>> byGroup = inputIndex.get(target.getItem());
            return byGroup == null ? new ArrayList<>() : new ArrayList<>(byGroup.values());
        }

        /** One representative entry per recipe group (drops split duplicates). */
        private List<RecipeHolder<?>> distinctRecipes() {
            Map<Object, RecipeHolder<?>> byGroup = new HashMap<>();
            for (RecipeHolder<?> entry : recipes) {
                Object group = entryGroups.get(entry);
                byGroup.putIfAbsent(group != null ? group : entry, entry);
            }
            return new ArrayList<>(byGroup.values());
        }
    }

    /** JEI-entry analogue of {@link RecipeTypeData}. */
    private static final class JeiTypeData {
        final String uid;
        final List<JeiEntry> entries = new ArrayList<>();
        final Set<Item> stationItems = new LinkedHashSet<>();
        final Map<Item, List<JeiEntry>> outputIndex = new HashMap<>();
        /** input item → (recipe object → one representative entry). */
        final Map<Item, Map<Object, JeiEntry>> inputIndex = new HashMap<>();

        JeiTypeData(String uid, List<ItemStack> stations) {
            this.uid = uid;
            if (stations != null) {
                for (ItemStack station : stations) {
                    if (station != null && !station.isEmpty()) {
                        stationItems.add(station.getItem());
                    }
                }
            }
        }

        void addEntry(JeiEntry entry) {
            entries.add(entry);
            if (entry.outputs() != null) {
                for (ItemStack output : entry.outputs()) {
                    if (output != null && !output.isEmpty()) {
                        outputIndex.computeIfAbsent(output.getItem(), k -> new ArrayList<>()).add(entry);
                    }
                }
            }
            if (entry.inputs() != null) {
                for (ItemStack input : entry.inputs()) {
                    if (input != null && !input.isEmpty()) {
                        inputIndex.computeIfAbsent(input.getItem(), k -> new HashMap<>())
                                .putIfAbsent(entry.recipe(), entry);
                    }
                }
            }
        }

        List<JeiEntry> resultsFor(ItemStack target) {
            List<JeiEntry> hits = outputIndex.get(target.getItem());
            return hits == null ? new ArrayList<>() : new ArrayList<>(hits);
        }

        List<JeiEntry> usagesFor(ItemStack target) {
            if (stationItems.contains(target.getItem())) return new ArrayList<>(entries);
            Map<Object, JeiEntry> byGroup = inputIndex.get(target.getItem());
            return byGroup == null ? new ArrayList<>() : new ArrayList<>(byGroup.values());
        }
    }
}
