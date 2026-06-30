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

import java.util.*;

public final class PartialCraftingUtil {

    // ── Core data stores ─────────────────────────────────────────────
    // WeakHashMap keyed directly by RecipeCollection instance.
    // Entries are auto-cleaned when RecipeCollections are GC'd.
    private static final WeakHashMap<RecipeCollection, Set<ResourceLocation>> PARTIAL_RECIPES = new WeakHashMap<>();
    private static final WeakHashMap<RecipeCollection, Integer> CHECKED_COLLECTIONS = new WeakHashMap<>();

    private static int filteringGeneration;
    private static boolean filteringActive;

    /**
     * Set by {@link #requestForceFullRefresh()} when the pipeline needs the
     * next {@code updateCollections} call to run the full vanilla+BRBE
     * cycle (vanilla forEach + partial marking) even when the inventory
     * hasn't changed.  Consumed by {@link #consumeForceFullRefresh()}.
     */
    private static volatile boolean forceFullRefresh = false;

    private PartialCraftingUtil() {}

    /**
     * Clear all internal caches and force a full rebuild on the next
     * {@code updateCollections} pass.  Call when config options affecting
     * partial-craftable display change (e.g. {@code partialMarkingEnabled}
     * or {@code partialCraftingEnabled} toggled).
     */
    public static void resetAllCaches() {
        PARTIAL_RECIPES.clear();
        CHECKED_COLLECTIONS.clear();
        filteringGeneration = 0;
        filteringActive = false;
        forceFullRefresh = true;
    }

    private static boolean enabled() {
        return BetterRecipeBook.ctx().config().partialMarkingEnabled;
    }

    public static void beginFilteringUpdate(boolean active) {
        filteringActive = active;
        if (active) {
            if (filteringGeneration == Integer.MAX_VALUE) {
                PARTIAL_RECIPES.clear();
                CHECKED_COLLECTIONS.clear();
                filteringGeneration = 0;
            }
            filteringGeneration++;
        }
    }

    /**
     * Request that the next {@code updateCollections} call forces a full
     * refresh (vanilla {@code canCraft} + partial marking) regardless of
     * whether the inventory has changed.
     *
     * <p>Called from {@code populatePage()} after it finishes its best-effort
     * partial marking, because {@code populatePage} cannot call vanilla's
     * {@code canCraft} to rebuild the craftable set from ground truth.
     * The forced refresh ensures the next user interaction produces
     * correct results.
     */
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
        PARTIAL_RECIPES.clear();
        CHECKED_COLLECTIONS.clear();
        filteringGeneration = 0;
    }

    /**
     * Atomically mark partial recipes AND inject them into the craftable
     * set.  Both the PARTIAL_RECIPES map and {@code brbe$getCraftable()}
     * must be updated together, otherwise RecipeButtons show wrong
     * textures (partials look craftable or vice versa).
     */
    public static void markAndInject(RecipeCollection collection, Set<Item> inventoryItems) {
        boolean marked = markPartialMaterials(collection, inventoryItems);
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

    /**
     * Simple 64-bit hash of slot state (item presence + counts).
     */
    public static long slotHash(NonNullList<Slot> slots) {
        long h = 1;
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                h = 31 * h + (long) stack.getItem().hashCode();
                h = 31 * h + stack.getCount();
            }
        }
        return h;
    }

    public static Set<Item> hashInventory(NonNullList<Slot> slots) {
        Set<Item> inventoryItems = new HashSet<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryItems.add(stack.getItem());
            }
        }
        return inventoryItems;
    }

    /**
     * Checks all recipes in the collection and marks those that have some
     * (but not all) matching ingredients.  Uses pre-hashed inventory set
     * for O(1) ingredient lookup.
     */
    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        return markPartialMaterials(collection, hashInventory(slots));
    }

    /**
     * Checks all recipes in the collection using a pre-hashed inventory set.
     */
    public static boolean markPartialMaterials(RecipeCollection collection, Set<Item> inventoryItems) {
        if (!enabled()) return false;
        if (wasCheckedForPartialMaterials(collection)) return hasPartialMaterials(collection);

        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        boolean markedAny = false;
        Set<ResourceLocation> partialRecipes = new HashSet<>();

        for (RecipeHolder<?> recipe : collection.getRecipes()) {
            // Skip recipes that are already fully craftable —
            // this guarantees isPartiallyCraftable() is mutually exclusive
            // with isCraftable(), so RecipeButtonMixin doesn't need a guard.
            if (collection.isCraftable(recipe)) {
                continue;
            }

            Recipe<?> vanillaRecipe = recipe.value();
            if (hasMatchingIngredientFast(vanillaRecipe.getIngredients(), inventoryItems)) {
                partialRecipes.add(recipe.id());
                markedAny = true;
            }
        }

        if (markedAny) {
            PARTIAL_RECIPES.put(collection, partialRecipes);
        } else {
            PARTIAL_RECIPES.remove(collection);
        }

        return markedAny;
    }

    public static void markPartialMaterial(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return;
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        PARTIAL_RECIPES.put(collection, new HashSet<>(Collections.singleton(recipeId)));
    }

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Integer generation = CHECKED_COLLECTIONS.get(collection);
        return filteringActive && generation != null && generation == filteringGeneration;
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeHolder<?> recipe) {
        return isPartiallyCraftable(collection, recipe.id());
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, ResourceLocation recipeId) {
        if (!enabled()) return false;
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(collection);
        return partialRecipes != null && partialRecipes.contains(recipeId);
    }

    public static boolean hasPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(collection);
        return partialRecipes != null && !partialRecipes.isEmpty();
    }

    /**
     * Classifies a collection by iterating its recipes and checking each
     * against both {@link #isPartiallyCraftable} and the vanilla craftable
     * set.  This is the single source of truth for collection classification;
     * sort methods and button mixins should use this instead of inline loops.
     */
    public static CollectionCategory categorize(RecipeCollection c) {
        if (!enabled()) return CollectionCategory.UNASSIGNED;

        boolean truly = false, partial = false;
        for (RecipeHolder<?> holder : c.getRecipes()) {
            if (isPartiallyCraftable(c, holder)) {
                partial = true;
            } else if (c.isCraftable(holder)) {
                truly = true;
            }
        }

        if (truly) return CollectionCategory.TRULY_CRAFTABLE;
        if (partial) return CollectionCategory.PARTIAL;
        return CollectionCategory.UNASSIGNED;
    }

    public static List<RecipeHolder<?>> getPartiallyCraftableRecipes(RecipeCollection collection) {
        if (!enabled()) return Collections.emptyList();
        Set<ResourceLocation> partialRecipes = PARTIAL_RECIPES.get(collection);
        if (partialRecipes == null || partialRecipes.isEmpty()) {
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

    /**
     * Legacy matching using raw slots (slower, kept for backward compat).
     */
    private static boolean hasMatchingIngredient(List<Ingredient> ingredients, NonNullList<Slot> slots) {
        Set<Item> inventoryItems = hashInventory(slots);
        return hasMatchingIngredientFast(ingredients, inventoryItems);
    }

    /**
     * Fast matching using pre-hashed inventory set — O(1) per ingredient.
     */
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
}
