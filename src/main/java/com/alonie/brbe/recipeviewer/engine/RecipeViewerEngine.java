package com.alonie.brbe.recipeviewer.engine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JEI-style query engine: a {@code recipeType -> recipes} registry plus an
 * item&nbsp;→&nbsp;recipe reverse index per lookup role, replacing the old
 * O(n) full-scan of {@code ClientRecipeBook.known}.  R (recipe) lookups hit the
 * OUTPUT index, U (usage) lookups hit the INPUT index (with the JEI
 * workstation short-circuit — a workstation block returns the whole type).
 *
 * <p>A type is a JEI recipe type id ({@code minecraft:crafting},
 * {@code minecraft:smelting}, {@code farmersdelight:cooking}, …).  The UI
 * category layer may aggregate several types (the furnace category aggregates
 * smelting / blasting / smoking / campfire_cooking); that aggregation and its
 * dedup live in {@code RecipeViewerCategories}, not here.</p>
 */
public final class RecipeViewerEngine {

    private RecipeViewerEngine() {}

    /** A recipe plus its already-extracted input and output item stacks.
     *  Split entries of one source recipe share the same {@code groupKey}, so
     *  usage lookups can show the recipe once instead of once per product. */
    public record IndexedRecipe(RecipeDisplayEntry entry, List<ItemStack> inputs, List<ItemStack> outputs,
                                Object groupKey) {
        public IndexedRecipe(RecipeDisplayEntry entry, List<ItemStack> inputs, List<ItemStack> outputs) {
            this(entry, inputs, outputs, null);
        }
    }

    /** One slot of a mod recipe's native layout: its position (relative to the
     *  recipe layout), its JEI {@code RecipeIngredientRole} ordinal and the item
     *  stacks it holds. */
    public record RecipeSlotLayout(int x, int y, int role, List<ItemStack> stacks) {}

    /** A mod recipe's background texture (its JEI category's background), as
     *  declared via {@code IGuiHelper.createDrawable/drawableBuilder}.  The
     *  region {@code (u,v,w,h)} lives in a texture of {@code textureWidth} x
     *  {@code textureHeight} (256 x 256 for the default {@code createDrawable}). */
    public record RecipeBackground(Identifier texture, int u, int v, int width, int height,
                                   int textureWidth, int textureHeight) {}

    /** A mod recipe's native layout (as declared by its JEI category's
     *  {@code setRecipe}): the layout dimensions, the visible slots and the
     *  category's background texture (nullable when the category declares none). */
    public record RecipeLayout(int width, int height, List<RecipeSlotLayout> slots, RecipeBackground background) {}

    /** The seven vanilla JEI recipe type ids.  {@link #clearVanilla()} only
     *  drops these, leaving mod-registered types (from the companion mod)
     *  intact across recipe-book rebuilds.
     *
     *  <p>{@code minecraft:smithing} is included even though the headless side
     *  skips its {@code registerType}: its authoritative data is the recipe
     *  book known set (per world), so a world whose known set has no smithing
     *  entries must not inherit the previous world's type via
     *  {@code registerType}-replace (which never runs for an empty group) —
     *  otherwise the usage query leaks the previous save's unlocks.
     *  {@code minecraft:brewing} needs no place here: it is re-registered from
     *  its {@code BookTypeSource} on every rebuild (source always non-empty). */
    private static final Set<String> VANILLA_TYPES = Set.of(
            "minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:smithing");

    private static final Map<String, RecipeTypeData> TYPES = new LinkedHashMap<>();
    private static final Map<RecipeDisplayId, RecipeDisplayEntry> BY_ID = new HashMap<>();
    private static final Map<RecipeDisplayId, RecipeLayout> LAYOUTS = new HashMap<>();
    private static final List<Runnable> REBUILD_LISTENERS = new CopyOnWriteArrayList<>();

    /** Register (or replace) a recipe type's full recipe list and its
     *  workstation block items.  Builds the OUTPUT/INPUT reverse indices. */
    public static void registerType(String uid, List<IndexedRecipe> recipes, List<ItemStack> stations) {
        if (uid == null) return;
        RecipeTypeData data = new RecipeTypeData(uid, stations);
        if (recipes != null) {
            for (IndexedRecipe recipe : recipes) {
                if (recipe == null || recipe.entry() == null) continue;
                data.addRecipe(recipe.entry(), recipe.inputs(), recipe.outputs(), recipe.groupKey());
                BY_ID.put(recipe.entry().id(), recipe.entry());
            }
        }
        TYPES.put(uid, data);
    }

    /** Recipes of {@code uid} whose result is {@code target} (R). */
    public static List<RecipeDisplayEntry> resultsFor(String uid, ItemStack target) {
        RecipeTypeData data = TYPES.get(uid);
        if (data == null || target == null || target.isEmpty()) return List.of();
        return data.resultsFor(target);
    }

    /** Recipes of {@code uid} using {@code target} as material (U).  A
     *  workstation block of this type returns the whole type (JEI semantics). */
    public static List<RecipeDisplayEntry> usagesFor(String uid, ItemStack target) {
        RecipeTypeData data = TYPES.get(uid);
        if (data == null || target == null || target.isEmpty()) return List.of();
        return data.usagesFor(target);
    }

    /** Every recipe of {@code uid}, unfiltered. */
    public static List<RecipeDisplayEntry> allRecipes(String uid) {
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
        List<RecipeDisplayEntry> hits = usage ? usagesFor(uid, target) : resultsFor(uid, target);
        return !hits.isEmpty();
    }

    /** The entry registered for {@code id} (covers synthetic entries from the
     *  companion mod, which are not present in {@code ClientRecipeBook.known}). */
    public static RecipeDisplayEntry entryFor(RecipeDisplayId id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Whether {@code id} is a synthetic id minted by the companion mod
     *  (in the {@code Integer.MIN_VALUE} range, disjoint from
     *  {@code VanillaRecipeCache}'s {@code -1,-2,…} range). */
    public static boolean isSynthetic(RecipeDisplayId id) {
        return id != null && id.index() < -10_000;
    }

    /** Register a mod recipe's native layout (used for its hover rendering). */
    public static void registerLayout(RecipeDisplayId id, RecipeLayout layout) {
        if (id != null && layout != null) {
            LAYOUTS.put(id, layout);
        }
    }

    /** The native layout registered for {@code id}, or null. */
    public static RecipeLayout getLayout(RecipeDisplayId id) {
        return id == null ? null : LAYOUTS.get(id);
    }

    public static void clear() {
        TYPES.clear();
        BY_ID.clear();
        LAYOUTS.clear();
    }

    /** Drop only the vanilla types (called before a recipe-book rebuild so the
     *  mod-registered types from the companion mod survive).  Also drops the
     *  dropped types' display ids from {@code BY_ID}/{@code LAYOUTS} via
     *  {@link #clearType}: display ids are server-assigned per world and may
     *  collide across world joins, so stale ids must not linger (cross-save
     *  query/popup leaks). */
    public static void clearVanilla() {
        for (String uid : VANILLA_TYPES) {
            if (TYPES.containsKey(uid)) {
                clearType(uid);
            }
        }
    }

    /** Drop one recipe type: its registry entry, every entry id it registered
     *  and every layout bound to those ids.  Used to source-exclude a plugin
     *  type (e.g. the no-recipe-book anvil / brewing / grindstone types when
     *  "hide objects of workstations without a recipe book" is on). */
    public static void clearType(String uid) {
        if (uid == null) return;
        RecipeTypeData data = TYPES.remove(uid);
        if (data == null) return;
        for (RecipeDisplayEntry recipe : data.recipes) {
            RecipeDisplayId id = recipe.id();
            if (id != null) {
                BY_ID.remove(id);
                LAYOUTS.remove(id);
            }
        }
    }

    /** Whether {@code uid} is one of the seven vanilla recipe types (managed
     *  by {@code RecipeViewerIndex.rebuildEngine}, not by the plugin collector). */
    public static boolean isVanillaType(String uid) {
        return uid != null && VANILLA_TYPES.contains(uid);
    }

    /** Workstation items of recipe-book-backed recipe types: the vanilla types
     *  (furnace, crafting table, …) plus mod types driven by their recipe book
     *  (e.g. Farmer's Delight's cooking pot).  Rebuilt by the plugin collector
     *  after each collection.  Used by the "hide objects of workstations
     *  without a recipe book" filter. */
    private static final Set<Item> RECIPE_BOOK_STATION_ITEMS = new HashSet<>();

    /** Recipe types ever observed as recipe-book driven (their entries
     *  appeared in the recipe book's known set during this session).  Once a
     *  type qualifies it stays qualified for the session: a re-collection that
     *  happens before the known set is synced (or with zero unlocked entries)
     *  must not silently drop its workstations from the legal set. */
    private static final Set<String> RECIPE_BOOK_TYPES = new HashSet<>();

    /** Record a recipe type as recipe-book backed (called when its entries are
     *  attributed from the known set).  Session-persistent. */
    public static void registerRecipeBookType(String uid) {
        if (uid != null) {
            RECIPE_BOOK_TYPES.add(uid);
        }
    }

    /** Whether {@code uid} is a recipe-book-backed type (vanilla recipe book
     *  or a mod type driven by its recipe book). */
    public static boolean isRecipeBookType(String uid) {
        return isVanillaType(uid) || RECIPE_BOOK_TYPES.contains(uid);
    }

    /** 进度模式（"隐藏无配方书工作站所属的对象"）下配方书体系的 vanilla 类型
     *  uid：合成/烧炼家族/锻造/酿造——酿造配方书由 BRBE 自带；切石/铁砧/研磨
     *  无配方书（known 集跟踪切石条目 ≠ 配方书体系：切石机屏幕无配方书）。 */
    public static final Set<String> PROGRESS_VANILLA_TYPES = Set.of(
            "minecraft:crafting", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:smithing",
            "minecraft:brewing");

    /** 进度模式类型：配方书体系的 vanilla 类型或已归属的配方书驱动 mod 类型。 */
    public static boolean isProgressType(String uid) {
        return PROGRESS_VANILLA_TYPES.contains(uid) || RECIPE_BOOK_TYPES.contains(uid);
    }

    /** Replace the recipe-book-backed workstation set.  Called by the JEI
     *  plugin collector with every workstation of the vanilla types plus every
     *  workstation of recipe-book-driven mod types. */
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

    /** Whether {@code station} belongs to a recipe-book-backed recipe type
     *  (vanilla recipe book or a mod type driven by its recipe book). */
    public static boolean isRecipeBookStation(ItemStack station) {
        return station != null && !station.isEmpty()
                && RECIPE_BOOK_STATION_ITEMS.contains(station.getItem());
    }


    /** 合并工作站物品（幂等）：工作站表（内置/外置/配置，进度模式过滤后视图）
     *  的站并入引擎类型——headless 注册的 stonecutting/smithing 类站可能为空，
     *  查询命中（usage 工作站短路）需要它。 */
    public static void addStations(String uid, java.util.Collection<ItemStack> stations) {
        RecipeTypeData data = TYPES.get(uid);
        if (data == null || stations == null) return;
        for (ItemStack stack : stations) {
            if (stack != null && !stack.isEmpty()) data.stationItems.add(stack.getItem());
        }
    }

    /** Every registered type uid (progress-mode legal-station rebuild). */
    public static List<String> typeUids() {
        return new ArrayList<>(TYPES.keySet());
    }

    /** Workstation items registered with {@code uid} (progress-mode legal set). */
    public static List<ItemStack> stationItemsOf(String uid) {
        RecipeTypeData data = TYPES.get(uid);
        if (data == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (Item item : data.stationItems) {
            out.add(new ItemStack(item));
        }
        return out;
    }

    /** Register a callback run after each vanilla recipe-book rebuild (i.e. after
     *  the server's recipe sync).  The companion mod uses this to re-collect mod
     *  recipes once the synchronised recipe registry is populated. */
    public static void registerRebuildListener(Runnable listener) {
        if (listener != null) {
            REBUILD_LISTENERS.add(listener);
        }
    }

    /** Invoke all registered rebuild listeners. */
    public static void notifyRebuilt() {
        for (Runnable listener : REBUILD_LISTENERS) {
            try {
                listener.run();
            } catch (Exception e) {
                // a broken listener must not break the recipe-book rebuild
            }
        }
    }

    private static final class RecipeTypeData {
        final String uid;
        final List<RecipeDisplayEntry> recipes = new ArrayList<>();
        final Set<Item> stationItems = new LinkedHashSet<>();
        /** output item → distinct entries producing it.  A recipe whose result
         *  resolves to several stacks of the SAME item (e.g. an enchanted sword
         *  at Sharpness I–V differing only in enchant NBT) contributes its
         *  entry ONCE — otherwise the viewer shows N identical objects each
         *  cycling through the same levels. */
        final Map<Item, Set<RecipeDisplayEntry>> outputIndex = new HashMap<>();
        /** input item → (recipe group → one representative entry).  Split
         *  entries of one recipe share a group, so a usage lookup shows the
         *  recipe once instead of once per product. */
        final Map<Item, Map<Object, RecipeDisplayEntry>> inputIndex = new HashMap<>();
        final Map<RecipeDisplayEntry, Object> entryGroups = new HashMap<>();

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

        void addRecipe(RecipeDisplayEntry entry, List<ItemStack> inputs, List<ItemStack> outputs, Object groupKey) {
            recipes.add(entry);
            entryGroups.put(entry, groupKey);
            if (outputs != null) {
                for (ItemStack output : outputs) {
                    if (output != null && !output.isEmpty()) {
                        // Set: the same entry is added once per output item even
                        // if its result resolves to several stacks of that item
                        // (enchantment levels / other NBT-only variants).
                        outputIndex.computeIfAbsent(output.getItem(), k -> new java.util.LinkedHashSet<>()).add(entry);
                    }
                }
            }
            if (inputs != null) {
                for (ItemStack input : inputs) {
                    if (input != null && !input.isEmpty()) {
                        // A null groupKey (un-split recipe) keys on the entry
                        // itself so it is never collapsed with its peers; only
                        // split groups collapse to one representative.
                        Object key = groupKey != null ? groupKey : entry;
                        inputIndex.computeIfAbsent(input.getItem(), k -> new HashMap<>())
                                .putIfAbsent(key, entry);
                    }
                }
            }
        }

        List<RecipeDisplayEntry> resultsFor(ItemStack target) {
            Set<RecipeDisplayEntry> hits = outputIndex.get(target.getItem());
            return hits == null ? new ArrayList<>() : new ArrayList<>(hits);
        }

        List<RecipeDisplayEntry> usagesFor(ItemStack target) {
            if (stationItems.contains(target.getItem())) return distinctRecipes();
            Map<Object, RecipeDisplayEntry> byGroup = inputIndex.get(target.getItem());
            return byGroup == null ? new ArrayList<>() : new ArrayList<>(byGroup.values());
        }

        /** One representative entry per recipe group (drops split duplicates);
         *  un-split recipes (null group) each key on their own entry. */
        private List<RecipeDisplayEntry> distinctRecipes() {
            Map<Object, RecipeDisplayEntry> byGroup = new HashMap<>();
            for (RecipeDisplayEntry entry : recipes) {
                Object group = entryGroups.get(entry);
                byGroup.putIfAbsent(group != null ? group : entry, entry);
            }
            return new ArrayList<>(byGroup.values());
        }
    }
}
