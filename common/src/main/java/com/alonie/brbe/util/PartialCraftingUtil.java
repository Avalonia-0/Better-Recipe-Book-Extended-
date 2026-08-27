package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.BrbeLogger;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.*;

/**
 * Detects recipes that are <em>partially craftable</em> — the player has
 * some (but not all) matching ingredients.
 *
 * <p>Data is stored via {@link RecipeCollectionTagger} for generation-aware
 * lifecycle management.  Generation-aware queries (prefixed with
 * {@code CurrentGen}) see only data from the current marking cycle;
 * {@code EvenIfStale} queries see data from any generation.</p>
 *
 * <p>The legacy methods ({@link #isPartiallyCraftable},
 * {@link #hasPartialMaterials}) use EvenIfStale semantics to preserve
 * backward compatibility with Step 0 cleanup code that runs after
 * generation advancement but before re-marking.</p>
 */
public final class PartialCraftingUtil {

    // ── Core data store ──────────────────────────────────────────────
    private static final RecipeCollectionTagger<ResourceLocation> tagger =
            new RecipeCollectionTagger<>();

    /**
     * Legacy force-refresh flag.  Gradually being replaced by the config
     * event bus; kept here until Phase 2 merge fully migrates callers.
     */
    private static volatile boolean forceFullRefresh = false;

    private PartialCraftingUtil() {}

    // ── Lifecycle ────────────────────────────────────────────────────

    /**
     * Force a full rebuild on the next {@code updateCollections} pass.
     * Call when config options affecting partial-craftable display change.
     *
     * <p>IMPORTANT: does NOT clear tagger data — the redirect cleanup
     * path in {@code incompletecrafting/RecipeBookComponentMixin} needs it
     * to identify which entries to purge from the vanilla craftable set.</p>
     */
    public static void invalidateCaches() {
        tagger.clearAll();   // clear generation marks so re-evaluation happens
        tagger.reset();      // reset generation counter
        forceFullRefresh = true;
    }

    private static boolean enabled() {
        return BetterRecipeBook.ctx().config().partialMarkingEnabled;
    }

    /**
     * Delegates to {@link RecipeCollectionTagger#beginFiltering}.
     * When {@code active} is true, increments the generation counter so
     * stale data from previous cycles is invisible to generation-aware
     * queries.  When false, preserves the current generation.
     */
    public static void beginFilteringUpdate(boolean active) {
        tagger.beginFiltering(active);
    }

    // ── Force-full-refresh (legacy — to be replaced in Phase 2) ─────

    public static void requestForceFullRefresh() {
        forceFullRefresh = true;
    }

    /** Consume the force-full-refresh flag (atomic read + clear). */
    public static boolean consumeForceFullRefresh() {
        boolean v = forceFullRefresh;
        forceFullRefresh = false;
        return v;
    }

    /**
     * Clear all partial-craftable caches.  Called when config changes
     * (save listener) so the next marking cycle starts fresh.
     */
    public static void clearCaches() {
        tagger.clearAll();
    }

    // ── Atomic marking + injection ───────────────────────────────────

    /**
     * Atomically mark partial recipes AND inject them into the craftable
     * set.  Both the tagger data and {@code brbe$getCraftable()}
     * must be updated together, otherwise RecipeButtons show wrong
     * textures (partials look craftable or vice versa).
     */
    public static void markAndInject(RecipeCollection collection, Set<Item> inventoryItems) {
        markAndInject(collection, inventoryItems, inventoryItems);
    }

    /**
     * {@code matchItems} 单独控制"哪些配方会被检索为残缺"（与 inventoryItems
     * 解耦）。partialOnlyWhenCarrying 场景：matchItems 仅含 carried 类型
     * （或空集 → 完全不标 partial），3×3 材料齐全判定仍用完整库存。
     */
    public static void markAndInject(RecipeCollection collection, Set<Item> inventoryItems, Set<Item> matchItems) {
        boolean marked = markPartialMaterials(collection, inventoryItems, matchItems);
        if (!hasPartialMaterials(collection)) return;
        int injected = 0;
        var ca = (RecipeCollectionAccessor) collection;
        for (var holder : collection.getRecipes()) {
            if (isPartiallyCraftable(collection, holder.id())) {
                ca.brbe$getCraftable().add(holder);
                injected++;
            }
        }
        if (BrbeLogger.isEnabled() && injected > 0) {
            BrbeLogger.log(BrbeLogger.Category.STATE,
                    "markAndInject: marked=%s injected=%d/%d recipes",
                    marked, injected, collection.getRecipes().size());
        }
    }

    // ── Slot / inventory hashing ─────────────────────────────────────

    /** Simple 64-bit hash of slot state (item presence + counts). */
    public static long slotHash(NonNullList<Slot> slots) {
        return slotHash(slots, ItemStack.EMPTY);
    }

    /**
     * 包含鼠标拿起物品（carried）的哈希。拿起/放下物品会改变哈希，
     * 从而触发配方书重新标记（否则 cache 跳过导致标记不更新）。
     */
    public static long slotHash(NonNullList<Slot> slots, ItemStack carried) {
        long h = 1;
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                h = 31 * h + (long) stack.getItem().hashCode();
                h = 31 * h + stack.getCount();
            }
        }
        if (carried != null && !carried.isEmpty()) {
            h = 31 * h + (long) carried.getItem().hashCode();
            h = 31 * h + carried.getCount();
        }
        return h;
    }

    public static Set<Item> hashInventory(NonNullList<Slot> slots) {
        return hashInventory(slots, -1);
    }

    /**
     * Like {@link #hashInventory} but skips the slot at {@code excludeIndex}
     * (or no slot if {@code excludeIndex < 0}).  Used to exclude the recipe
     * result slot: the crafted product sits in the result slot and must not
     * count as an available material, otherwise a just-crafted product would
     * make every recipe needing it appear "partially craftable".
     */
    public static Set<Item> hashInventory(NonNullList<Slot> slots, int excludeIndex) {
        return hashInventory(slots, excludeIndex, ItemStack.EMPTY);
    }

    /**
     * 包含鼠标拿起物品（carried）的库存类型集。
     */
    public static Set<Item> hashInventory(NonNullList<Slot> slots, int excludeIndex, ItemStack carried) {
        Set<Item> inventoryItems = new HashSet<>();
        for (Slot slot : slots) {
            if (slot.index == excludeIndex) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryItems.add(stack.getItem());
            }
        }
        if (carried != null && !carried.isEmpty()) {
            inventoryItems.add(carried.getItem());
        }
        return inventoryItems;
    }

    // ── Marking ──────────────────────────────────────────────────────

    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        return markPartialMaterials(collection, hashInventory(slots));
    }

    /**
     * Checks all recipes in the collection using a pre-hashed inventory set.
     */
    public static boolean markPartialMaterials(RecipeCollection collection, Set<Item> inventoryItems) {
        return markPartialMaterials(collection, inventoryItems, inventoryItems);
    }

    /**
     * {@code matchItems} 单独控制"哪些配方会被检索为残缺"（与 inventoryItems
     * 解耦）。partialOnlyWhenCarrying 场景：matchItems 仅含 carried 类型
     * （或空集 → 完全不标 partial），3×3 材料齐全判定仍用完整库存。
     */
    public static boolean markPartialMaterials(RecipeCollection collection, Set<Item> inventoryItems, Set<Item> matchItems) {
        return markPartialMaterials(collection, inventoryItems, matchItems, false);
    }

    /**
     * {@code twoByTwoInventory}：当前是否为 2×2 生存背包网格。仅当为 true 且
     * showAllRecipesInSurvival 关闭时才跳过 3×3 配方；工作台（3×3 网格）不受影响。
     */
    public static boolean markPartialMaterials(RecipeCollection collection, Set<Item> inventoryItems, Set<Item> matchItems,
                                               boolean twoByTwoInventory) {
        if (!enabled()) return false;
        if (wasCheckedForPartialMaterials(collection)) return hasPartialMaterials(collection);

        tagger.markAsChecked(collection);
        boolean markedAny = false;
        Set<ResourceLocation> partialRecipes = new HashSet<>();

        for (RecipeHolder<?> recipe : collection.getRecipes()) {
            // Skip recipes that are already fully craftable —
            // this guarantees isPartiallyCraftable() is mutually exclusive
            // with isCraftable(), so RecipeButtonMixin doesn't need a guard.
            if (collection.isCraftable(recipe)) {
                continue;
            }

            // 3×3 配方：2×2 生存网格放不下。
            // showAllRecipesInSurvival 关闭时，仅 2×2 背包网格不显示 3×3 配方——
            // 不标 partial，否则会被注入 craftable 而残留显示（出现空气占位按钮）；
            // 工作台（3×3 网格）的 3×3 残缺配方照常标记。
            Recipe<?> vanillaRecipe = recipe.value();
            if (needsLargerGrid(recipe)) {
                if (twoByTwoInventory && !BetterRecipeBook.ctx().config().showAllRecipesInSurvival) continue;
                // 材料齐全 → 不标 partial（网格问题，由 incompatible 警告处理）。
                // 材料不足 → 标 partial（确实缺材料）。
                if (hasAllIngredients(vanillaRecipe.getIngredients(), inventoryItems)) {
                    continue;
                }
            }

            if (hasMatchingIngredientFast(vanillaRecipe.getIngredients(), matchItems)) {
                partialRecipes.add(recipe.id());
                markedAny = true;
            }
        }

        if (markedAny) {
            tagger.setAllTags(collection, partialRecipes);
        } else {
            tagger.clearTags(collection); // preserves checked-generation mark
        }

        return markedAny;
    }

    public static void markPartialMaterial(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return;
        tagger.markAsChecked(collection);
        tagger.addTag(collection, recipeId);
    }

    // ── Freshness ────────────────────────────────────────────────────

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.wasChecked(collection);
    }

    // ── Normal queries (generation-aware — see only current cycle) ───

    /**
     * True if the recipe is partially craftable according to the
     * <em>current</em> marking generation.
     */
    public static boolean isPartiallyCraftableCurrentGen(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return false;
        return tagger.hasTag(collection, recipeId);
    }

    /**
     * True if the collection has at least one partially-craftable recipe
     * in the <em>current</em> marking generation.
     */
    public static boolean hasPartialMaterialsCurrentGen(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.hasAnyTag(collection);
    }

    // ── EvenIfStale queries (see all generations) ────────────────────

    /**
     * True if the recipe is partially craftable, even if the data is from
     * a previous marking generation.  Use for Step 0 cleanup (removing
     * stale injections) and in contexts where generation state is unknown.
     */
    public static boolean isPartiallyCraftableEvenIfStale(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return false;
        return tagger.hasTagEvenIfStale(collection, recipeId);
    }

    /**
     * True if the collection has at least one partially-craftable recipe,
     * even if the data is from a previous marking generation.
     */
    public static boolean hasPartialMaterialsEvenIfStale(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.hasAnyTagEvenIfStale(collection);
    }

    // ── Legacy methods (EvenIfStale + enabled guard) ─────────────────
    // These preserve the old behavior where queries directly read the
    // map without generation awareness.  Step 0 cleanup in the
    // incompletecrafting Mixin relies on this to find stale entries
    // after beginFilteringUpdate(true) advances the generation.
    // Phase 2 will switch cleanup to EvenIfStale and these to CurrentGen.

    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeHolder<?> recipe) {
        return isPartiallyCraftable(collection, recipe.id());
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, ResourceLocation recipeId) {
        return isPartiallyCraftableEvenIfStale(collection, recipeId);
    }

    public static boolean hasPartialMaterials(RecipeCollection collection) {
        return hasPartialMaterialsEvenIfStale(collection);
    }

    // ── Raw queries (bypass enabled() guard + EvenIfStale) ───────────

    /**
     * Raw check — bypasses the {@link #enabled()} guard.  For cleanup code
     * that needs to purge partial-craftable state even after the feature has
     * been disabled in config.
     */
    public static boolean hasPartialMaterialsRaw(RecipeCollection collection) {
        return tagger.hasAnyTagEvenIfStale(collection);
    }

    /**
     * Raw check — bypasses the {@link #enabled()} guard.
     * @see #hasPartialMaterialsRaw(RecipeCollection)
     */
    public static boolean isPartiallyCraftableRaw(RecipeCollection collection, ResourceLocation recipeId) {
        return tagger.hasTagEvenIfStale(collection, recipeId);
    }

    // ── Mutation ─────────────────────────────────────────────────────

    /**
     * Removes a single recipe from the partial-craftable set for a
     * collection.  If the collection has no more partial recipes after
     * removal, the collection entry is dropped from the map entirely.
     *
     * <p>Used during the 3×3-grid cleanup pass on the inventory screen
     * (2×2 grid) — recipes that require a larger grid should never be
     * marked as partially craftable because the player may already have
     * all ingredients.</p>
     */
    public static void removePartialRecipe(RecipeCollection collection, ResourceLocation recipeId) {
        tagger.removeTag(collection, recipeId);
    }

    // ── Categorization ───────────────────────────────────────────────

    /**
     * Classifies a collection by iterating its recipes and checking each
     * against both {@link #isPartiallyCraftable} and the vanilla craftable
     * set.  This is the single source of truth for collection classification;
     * sort methods and button mixins should use this instead of inline loops.
     */
    public static CollectionCategory categorize(RecipeCollection c) {
        if (!enabled()) return CollectionCategory.UNASSIGNED;
        return categorizeImpl(c, false);
    }

    /**
     * Like {@link #categorize} but uses EvenIfStale queries so sorting
     * is stable across generation boundaries.  Use in pipeline sorting
     * to prevent category flicker on tab switches.
     */
    public static CollectionCategory categorizeEvenIfStale(RecipeCollection c) {
        if (!enabled()) return CollectionCategory.UNASSIGNED;
        return categorizeImpl(c, true);
    }

    private static CollectionCategory categorizeImpl(RecipeCollection c, boolean evenIfStale) {
        boolean truly = false, partial = false;
        for (RecipeHolder<?> holder : c.getRecipes()) {
            if (evenIfStale
                    ? isPartiallyCraftableEvenIfStale(c, holder.id())
                    : isPartiallyCraftable(c, holder.id())) {
                partial = true;
            } else if (c.isCraftable(holder)) {
                truly = true;
            }
        }

        if (truly) return CollectionCategory.TRULY_CRAFTABLE;
        if (partial) return CollectionCategory.PARTIAL;
        return CollectionCategory.UNASSIGNED;
    }

    // ── Bulk retrieval ───────────────────────────────────────────────

    public static List<RecipeHolder<?>> getPartiallyCraftableRecipes(RecipeCollection collection) {
        if (!enabled()) return Collections.emptyList();
        Set<ResourceLocation> partialRecipes = tagger.getTagsEvenIfStale(collection);
        if (partialRecipes.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (RecipeHolder<?> recipe : collection.getRecipes()) {
            if (partialRecipes.contains(recipe.id())) {
                recipes.add(recipe);
            }
        }

        return recipes;
    }

    // ── Ingredient matching ──────────────────────────────────────────

    @SuppressWarnings("unused")
    private static boolean hasMatchingIngredient(List<Ingredient> ingredients, NonNullList<Slot> slots) {
        Set<Item> inventoryItems = hashInventory(slots);
        return hasMatchingIngredientFast(ingredients, inventoryItems);
    }

    /** True if every ingredient is present in inventory (by type). */
    private static boolean hasAllIngredients(List<Ingredient> ingredients, Set<Item> inventoryItems) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            boolean found = false;
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty() && inventoryItems.contains(stack.getItem())) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean hasMatchingIngredientFast(List<Ingredient> ingredients, Set<Item> inventoryItems) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty() && inventoryItems.contains(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- 3×3 网格判定 ----

    /** True if any recipe in the collection needs a 3×3 crafting grid. */
    public static boolean hasAnyLargerGridRecipe(RecipeCollection collection) {
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (needsLargerGrid(holder)) return true;
        }
        return false;
    }

    /**
     * 配方是否需要 3×3 合成网格（2×2 生存背包网格放不下）。
     * 这类配方无论材料是否齐全，都不属于"缺少部分材料"——
     * 网格不够由 {@code IncompatibleCraftingUtil} 的警告处理。
     */
    public static boolean needsLargerGrid(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof ShapedRecipe shaped) {
            return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() > 4;
        }
        return false;
    }
}
