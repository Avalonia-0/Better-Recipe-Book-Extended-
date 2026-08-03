package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PartialCraftingUtil {
    private static final RecipeCollectionTagger<RecipeDisplayId> tagger = new RecipeCollectionTagger<>();

    private PartialCraftingUtil() {
    }

    /**
     * Force a full rebuild on the next {@code updateCollections} pass.
     * Call when config options affecting partial-craftable display change
     * (e.g. {@code partialMarkingEnabled} toggled).
     */
    public static void invalidateCaches() {
        tagger.clearAll();
        tagger.beginFiltering(false);
    }

    /**
     * Single point-of-control for the partial material marking feature.
     * All public methods check this before doing any work, so callers
     * never need to repeat the config gate.
     */
    private static boolean enabled() {
        return BetterRecipeBook.config.partialMarkingEnabled;
    }

    // ---- Generation / freshness (delegated to tagger) ----

    public static void beginFilteringUpdate(boolean active) {
        tagger.beginFiltering(active);
    }

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.wasChecked(collection);
    }

    // ---- Inventory hashing (unchanged) ----

    public static long slotHash(NonNullList<Slot> slots) {
        long h = 1;
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                h = 31 * h + (long)stack.getItem().hashCode();
                h = 31 * h + stack.getCount();
            }
        }
        return h;
    }

    public static java.util.Set<Item> hashInventory(NonNullList<Slot> slots) {
        java.util.Set<Item> inventoryItems = new java.util.HashSet<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryItems.add(stack.getItem());
            }
        }
        return inventoryItems;
    }

    // ---- Marking ----

    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        return markPartialMaterials(collection, hashInventory(slots));
    }

    public static boolean markPartialMaterials(RecipeCollection collection, java.util.Set<Item> inventoryItems) {
        if (!enabled()) return false;
        if (tagger.wasChecked(collection)) return tagger.hasAnyTag(collection);
        tagger.markAsChecked(collection);
        boolean markedAny = false;
        Set<RecipeDisplayId> partialRecipes = new HashSet<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (collection.isCraftable(recipe.id())) {
                continue;
            }

            // 3×3 配方：2×2 生存网格放不下，材料是否齐全都不标"缺少部分材料"。
            // 材料齐全的 3×3 配方（如铁剑）应显示不可合成 + incompatible 警告，
            // 而不是红色 partial 覆盖层。
            if (needsLargerGrid(recipe.display())) {
                continue;
            }

            if (recipe.craftingRequirements().map(requirements -> hasMatchingIngredientFast(requirements, inventoryItems)).orElse(false)
                    || hasMatchingDisplayIngredientFast(recipe.display(), inventoryItems)) {
                partialRecipes.add(recipe.id());
                markedAny = true;
            }
        }

        if (markedAny) {
            tagger.setAllTags(collection, partialRecipes);
        } else {
            tagger.clearTags(collection);
        }

        return markedAny;
    }

    public static void markPartialMaterial(RecipeCollection collection, RecipeDisplayId recipeDisplayId) {
        if (!enabled()) return;
        tagger.addTag(collection, recipeDisplayId);
        tagger.markAsChecked(collection);
    }

    /**
     * Removes a single recipe from the partial-materials set for a collection.
     * Used to undo over-aggressive marking (e.g. 3×3 recipes that can never be
     * crafted in the 2×2 survival-inventory grid).
     */
    public static void unmarkPartial(RecipeCollection collection, RecipeDisplayId id) {
        if (!enabled()) return;
        tagger.removeTag(collection, id);
    }

    // ---- Queries ----

    /**
     * Generation-aware query: true only if the recipe was marked as partial
     * in the <em>current</em> generation.  Safe for button rendering and
     * general use — never returns stale data.
     */
    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeDisplayId recipeDisplayId) {
        if (!enabled()) return false;
        return tagger.hasTag(collection, recipeDisplayId);
    }

    /**
     * Stale-data query: true even if the recipe was marked in a previous
     * generation.  Only Step 0 (undo-injection) and root-cause cleanup
     * should use this.
     */
    public static boolean isPartiallyCraftableEvenIfStale(RecipeCollection collection, RecipeDisplayId id) {
        if (!enabled()) return false;
        return tagger.hasTagEvenIfStale(collection, id);
    }

    /**
     * Raw query — bypasses the {@link #enabled()} guard.
     * Only Step 0 (undo-injection) should use this when the feature has
     * been toggled OFF and the standard queries would return false.
     */
    public static boolean isPartiallyCraftableRaw(RecipeCollection collection, RecipeDisplayId id) {
        return tagger.hasTagEvenIfStale(collection, id);
    }

    /**
     * Generation-aware query: true only if the collection has partial
     * materials marked in the <em>current</em> generation.
     */
    public static boolean hasPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.hasAnyTag(collection);
    }

    /**
     * Stale-data query: true even if partial data is from a previous
     * generation.  Only Step 0 should use this — it needs to know what
     * was injected last cycle so it can undo those injections.
     */
    public static boolean hasPartialMaterialsEvenIfStale(RecipeCollection collection) {
        if (!enabled()) return false;
        return tagger.hasAnyTagEvenIfStale(collection);
    }

    /**
     * Raw query — bypasses the {@link #enabled()} guard.
     * Only Step 0 (undo-injection) should use this when the feature has
     * been toggled OFF and the standard queries would return false.
     */
    public static boolean hasPartialMaterialsRaw(RecipeCollection collection) {
        return tagger.hasAnyTagEvenIfStale(collection);
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
        for (RecipeDisplayEntry entry : c.getRecipes()) {
            RecipeDisplayId id = entry.id();
            if (isPartiallyCraftable(c, id)) {
                partial = true;
            } else if (c.isCraftable(id)) {
                truly = true;
            }
        }
        if (truly) return CollectionCategory.TRULY_CRAFTABLE;
        if (partial) return CollectionCategory.PARTIAL;
        return CollectionCategory.UNASSIGNED;
    }

    public static List<RecipeDisplayEntry> getPartiallyCraftableRecipes(RecipeCollection collection) {
        if (!enabled()) return Collections.emptyList();
        if (!tagger.hasAnyTag(collection)) {
            return Collections.emptyList();
        }

        List<RecipeDisplayEntry> recipes = new ArrayList<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (tagger.hasTag(collection, recipe.id())) {
                recipes.add(recipe);
            }
        }

        return recipes;
    }

    public static List<RecipeDisplayEntry> getSelectedRecipes(RecipeCollection collection, RecipeCollection.CraftableStatus status) {
        if (!enabled()) return collection.getSelectedRecipes(status);
        List<RecipeDisplayEntry> selectedRecipes = collection.getSelectedRecipes(status);
        if (status != RecipeCollection.CraftableStatus.CRAFTABLE) {
            return selectedRecipes;
        }

        if (!tagger.hasAnyTag(collection)) {
            return selectedRecipes;
        }

        List<RecipeDisplayEntry> combinedRecipes = new ArrayList<>(selectedRecipes);
        Set<RecipeDisplayId> existingIds = new HashSet<>();
        for (RecipeDisplayEntry recipe : selectedRecipes) {
            existingIds.add(recipe.id());
        }

        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (tagger.hasTag(collection, recipe.id()) && !existingIds.contains(recipe.id())) {
                combinedRecipes.add(recipe);
            }
        }

        return combinedRecipes;
    }

    // ---- Ingredient matching helpers (unchanged) ----

    private static boolean hasMatchingIngredient(List<Ingredient> ingredients, NonNullList<Slot> slots) {
        return hasMatchingIngredientFast(ingredients, hashInventory(slots));
    }

    private static boolean hasMatchingIngredientFast(List<Ingredient> ingredients, java.util.Set<Item> inventoryItems) {
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }

            if (ingredient.items().anyMatch(holder -> inventoryItems.contains(holder.value()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingDisplayIngredient(RecipeDisplay display, NonNullList<Slot> slots) {
        return hasMatchingDisplayIngredientFast(display, hashInventory(slots));
    }

    private static boolean hasMatchingDisplayIngredientFast(RecipeDisplay display, java.util.Set<Item> inventoryItems) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return hasMatchingSlotDisplayFast(shaped.ingredients(), inventoryItems);
        }

        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return hasMatchingSlotDisplayFast(shapeless.ingredients(), inventoryItems);
        }

        return false;
    }

    private static boolean hasMatchingSlotDisplay(List<SlotDisplay> ingredients, NonNullList<Slot> slots) {
        return hasMatchingSlotDisplayFast(ingredients, hashInventory(slots));
    }

    private static boolean hasMatchingSlotDisplayFast(List<SlotDisplay> ingredients, java.util.Set<Item> inventoryItems) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        ContextMap context = SlotDisplayContext.fromLevel(minecraft.level);
        for (SlotDisplay ingredient : ingredients) {
            for (ItemStack candidate : ingredient.resolveForStacks(context)) {
                if (candidate.isEmpty()) {
                    continue;
                }

                if (inventoryItems.contains(candidate.getItem())) {
                    return true;
                }
            }
        }

        return false;
    }

    // ---- 3×3 网格判定 ----

    /**
     * 配方是否需要 3×3 合成网格（2×2 生存背包网格放不下）。
     * 这类配方无论材料是否齐全，都不属于"缺少部分材料"——
     * 网格不够由 {@code IncompatibleCraftingUtil} 的警告处理。
     */
    public static boolean needsLargerGrid(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() > 2 || shaped.height() > 2;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() > 4;
        }
        return false;
    }
}
