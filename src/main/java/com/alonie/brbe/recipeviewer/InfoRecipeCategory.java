package com.alonie.brbe.recipeviewer;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The information category (JEI {@code jei:information} type): a pure info
 * sheet like the compost / fuel categories — no recipe buttons, the queried
 * item alone in a grid, and its JEI info pages' text lines shown right in the
 * tooltip.
 *
 * <p>Data source is the JEI runtime's {@code jei:information} recipes (the
 * {@code addIngredientInfo} calls of every loaded plugin — the same data JEI
 * displays), available both from the real JEI and from the embedded headless
 * core.  Without a JEI runtime the category is simply absent.  Info text
 * applies to both R and U queries (JEI shows it on the recipe page).</p>
 */
public final class InfoRecipeCategory implements RecipeViewerCategory {

    /** The JEI manager the cached info recipes came from; replaced when a new
     *  runtime is bridged. */
    private static IRecipeManager cachedManager;
    private static List<IJeiIngredientInfoRecipe> cachedRecipes = List.of();

    @Override
    public String id() {
        return "info";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITTEN_BOOK);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.info");
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        // Info sheet: no recipe entries.
        return List.of();
    }

    @Override
    public boolean hasContent(ItemStack target, boolean usage) {
        return hasInfo(target);
    }

    @Override
    public boolean isGridCategory() {
        return true;
    }

    @Override
    public List<ItemStack> allGridItems() {
        List<ItemStack> out = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (IJeiIngredientInfoRecipe recipe : infoRecipes()) {
            for (ITypedIngredient<?> ingredient : recipe.getIngredients()) {
                if (ingredient == null) continue;
                try {
                    Object value = ingredient.getIngredient();
                    if (value instanceof ItemStack stack && !stack.isEmpty()
                            && seen.add(stack.getItem())) {
                        out.add(stack);
                    }
                } catch (Exception ignored) {
                    // one broken ingredient must not break the pass
                }
            }
        }
        return out;
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        return target != null && !target.isEmpty() && hasInfo(target)
                ? List.of(target) : List.of();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        // Lowest of the info/recipe categories: a real recipe category wins
        // the default tab; the info sheet is reachable via the tab.
        return hasInfo(target) ? 0 : -1;
    }

    /** Whether {@code target} has at least one JEI info page. */
    public boolean hasInfo(ItemStack target) {
        if (target == null || target.isEmpty()) return false;
        for (IJeiIngredientInfoRecipe recipe : infoRecipes()) {
            if (matches(recipe, target)) return true;
        }
        return false;
    }

    /** The info text lines of every JEI info page covering {@code target}
     *  (merged in registration order), or empty. */
    public List<FormattedText> descriptionFor(ItemStack target) {
        List<FormattedText> out = new ArrayList<>();
        if (target == null || target.isEmpty()) return out;
        for (IJeiIngredientInfoRecipe recipe : infoRecipes()) {
            if (matches(recipe, target)) {
                out.addAll(recipe.getDescription());
            }
        }
        return out;
    }

    private static boolean matches(IJeiIngredientInfoRecipe recipe, ItemStack target) {
        for (ITypedIngredient<?> ingredient : recipe.getIngredients()) {
            if (ingredient == null) continue;
            try {
                Object value = ingredient.getIngredient();
                if (value instanceof ItemStack stack && !stack.isEmpty()
                        && stack.getItem() == target.getItem()) {
                    return true;
                }
            } catch (Exception ignored) {
                // one broken ingredient must not break the match pass
            }
        }
        return false;
    }

    /** Every {@code jei:information} recipe of the current JEI runtime
     *  (cached per manager instance). */
    private static List<IJeiIngredientInfoRecipe> infoRecipes() {
        IRecipeManager manager = reflectRecipeManager();
        if (manager == null) return List.of();
        if (manager != cachedManager || cachedRecipes.isEmpty()) {
            cachedManager = manager;
            cachedRecipes = collect(manager);
        }
        return cachedRecipes;
    }

    /** headless-jei 运行时的 recipeManager（反射；absent → null）。 */
    private static IRecipeManager reflectRecipeManager() {
        try {
            Class<?> bridge = Class.forName("com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge");
            Object manager = bridge.getMethod("recipeManager").invoke(null);
            return (IRecipeManager) manager;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<IJeiIngredientInfoRecipe> collect(IRecipeManager manager) {
        try {
            List<IJeiIngredientInfoRecipe> out = new ArrayList<>();
            manager.createRecipeLookup(RecipeTypes.INFORMATION)
                    .get()
                    .forEach(recipe -> out.add(recipe));
            return out;
        } catch (Exception | LinkageError e) {
            return List.of();
        }
    }
}
