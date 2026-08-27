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

    // -- JEI (headless embedded runtime) entries ------------------------------

    /** A recipe collected from a JEI plugin (embedded headless core or real
     *  JEI): its JEI type uid, the raw recipe object (e.g. {@code
     *  IJeiAnvilRecipe} / a mod's recipe object) and its already-extracted
     *  item inputs/outputs.  Rendered by the popup through {@code
     *  IRecipeManager#createRecipeLayoutDrawable}. */
    public record JeiEntry(ResourceLocation typeUid, Object recipe,
                           List<ItemStack> inputs, List<ItemStack> outputs) {
    }

    private static final Map<String, JeiTypeData> JEI_TYPES = new LinkedHashMap<>();

    /** Register (or replace) a JEI type's recipes and its workstation items
     *  (collected from JEI plugins / vanilla JEI recipes). */
    public static void registerJeiType(String uid, List<JeiEntry> entries, List<ItemStack> stations) {
        if (uid == null) return;
        JeiTypeData data = new JeiTypeData(uid, stations);
        if (entries != null) {
            for (JeiEntry entry : entries) {
                if (entry != null && entry.recipe() != null) {
                    data.addEntry(entry);
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
