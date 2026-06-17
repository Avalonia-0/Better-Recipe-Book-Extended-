package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
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
import java.util.WeakHashMap;

public final class PartialCraftingUtil {
    private static final WeakHashMap<RecipeCollection, Set<RecipeDisplayId>> PARTIAL_RECIPES = new WeakHashMap<>();
    private static final WeakHashMap<RecipeCollection, Integer> CHECKED_COLLECTIONS = new WeakHashMap<>();
    private static int filteringGeneration;
    private static boolean filteringActive;

    private PartialCraftingUtil() {
    }

    /**
     * Single point-of-control for the partial material marking feature.
     * All public methods check this before doing any work, so callers
     * never need to repeat the config gate.
     */
    private static boolean enabled() {
        return BetterRecipeBook.config.partialMarkingEnabled;
    }

    public static void beginFilteringUpdate(boolean active) {
        filteringActive = active;
        if (active) {
            filteringGeneration++;
        }
    }

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

    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        return markPartialMaterials(collection, hashInventory(slots));
    }

    public static boolean markPartialMaterials(RecipeCollection collection, java.util.Set<Item> inventoryItems) {
        if (!enabled()) return false;
        if (wasCheckedForPartialMaterials(collection)) return hasPartialMaterials(collection);
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        boolean markedAny = false;
        Set<RecipeDisplayId> partialRecipes = new HashSet<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (collection.isCraftable(recipe.id())) {
                continue;
            }

            if (recipe.craftingRequirements().map(requirements -> hasMatchingIngredientFast(requirements, inventoryItems)).orElse(false)
                    || hasMatchingDisplayIngredientFast(recipe.display(), inventoryItems)) {
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

    public static void markPartialMaterial(RecipeCollection collection, RecipeDisplayId recipeDisplayId) {
        if (!enabled()) return;
        PARTIAL_RECIPES.put(collection, new HashSet<>(Collections.singleton(recipeDisplayId)));
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
    }

    /**
     * Removes a single recipe from the partial-materials set for a collection.
     * Used to undo over-aggressive marking (e.g. 3×3 recipes that can never be
     * crafted in the 2×2 survival-inventory grid).
     */
    public static void unmarkPartial(RecipeCollection collection, RecipeDisplayId id) {
        if (!enabled()) return;
        Set<RecipeDisplayId> set = PARTIAL_RECIPES.get(collection);
        if (set != null) {
            set.remove(id);
            if (set.isEmpty()) {
                PARTIAL_RECIPES.remove(collection);
            }
        }
    }

    public static boolean wasCheckedForPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Integer generation = CHECKED_COLLECTIONS.get(collection);
        return filteringActive && generation != null && generation == filteringGeneration;
    }

    public static boolean isPartiallyCraftable(RecipeCollection collection, RecipeDisplayId recipeDisplayId) {
        if (!enabled()) return false;
        Set<RecipeDisplayId> partialRecipes = PARTIAL_RECIPES.get(collection);
        return partialRecipes != null && partialRecipes.contains(recipeDisplayId);
    }

    public static boolean hasPartialMaterials(RecipeCollection collection) {
        if (!enabled()) return false;
        Set<RecipeDisplayId> partialRecipes = PARTIAL_RECIPES.get(collection);
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
        Set<RecipeDisplayId> partialRecipes = PARTIAL_RECIPES.get(collection);
        if (partialRecipes == null || partialRecipes.isEmpty()) {
            return Collections.emptyList();
        }

        List<RecipeDisplayEntry> recipes = new ArrayList<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (partialRecipes.contains(recipe.id())) {
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

        Set<RecipeDisplayId> partialRecipes = PARTIAL_RECIPES.get(collection);
        if (partialRecipes == null || partialRecipes.isEmpty()) {
            return selectedRecipes;
        }

        List<RecipeDisplayEntry> combinedRecipes = new ArrayList<>(selectedRecipes);
        Set<RecipeDisplayId> existingIds = new HashSet<>();
        for (RecipeDisplayEntry recipe : selectedRecipes) {
            existingIds.add(recipe.id());
        }

        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (partialRecipes.contains(recipe.id()) && !existingIds.contains(recipe.id())) {
                combinedRecipes.add(recipe);
            }
        }

        return combinedRecipes;
    }

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
}
