package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
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

    public static boolean markPartialMaterials(RecipeCollection collection, NonNullList<Slot> slots) {
        if (!enabled()) return false;
        CHECKED_COLLECTIONS.put(collection, filteringGeneration);
        boolean markedAny = false;
        Set<RecipeDisplayId> partialRecipes = new HashSet<>();
        for (RecipeDisplayEntry recipe : collection.getRecipes()) {
            if (collection.isCraftable(recipe.id())) {
                continue;
            }

            if (recipe.craftingRequirements().map(requirements -> hasMatchingIngredient(requirements, slots)).orElse(false)
                    || hasMatchingDisplayIngredient(recipe.display(), slots)) {
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

    public static void sortCraftableBeforePartial(List<RecipeCollection> collections) {
        if (!enabled()) return;
        List<RecipeCollection> craftableCollections = new ArrayList<>();
        List<RecipeCollection> partialCollections = new ArrayList<>();

        for (RecipeCollection collection : collections) {
            if (collection.hasCraftable()) {
                craftableCollections.add(collection);
            } else {
                partialCollections.add(collection);
            }
        }

        collections.clear();
        collections.addAll(craftableCollections);
        collections.addAll(partialCollections);
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
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }

            for (Slot slot : slots) {
                if (slot.hasItem() && ingredient.test(slot.getItem())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasMatchingDisplayIngredient(RecipeDisplay display, NonNullList<Slot> slots) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return hasMatchingSlotDisplay(shaped.ingredients(), slots);
        }

        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return hasMatchingSlotDisplay(shapeless.ingredients(), slots);
        }

        return false;
    }

    private static boolean hasMatchingSlotDisplay(List<SlotDisplay> ingredients, NonNullList<Slot> slots) {
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

                for (Slot slot : slots) {
                    if (slot.hasItem() && candidate.getItem().equals(slot.getItem().getItem())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
