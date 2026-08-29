package com.alonie.brbe.recipeviewer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The info category: JEI {@code jei:information} recipes as a standalone grid
 * (one cell per queryable item).  1.21.1 版——headless-jei 是独立 JIJ mod，
 * mezz 类不可编译依赖，经反射走 {@code JeiRuntimeBridge.recipeManager()}：
 * 无 JEI 运行时/无信息配方时类别自动缺席（hasContent false）。
 */
public final class InfoRecipeCategory implements RecipeViewerCategory {

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
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // 纯信息类别：无 RecipeHolder 条目（grid 渲染）。
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
        Set<Item> items = new LinkedHashSet<>();
        for (Object recipe : infoRecipes()) {
            items.addAll(ingredientsOf(recipe));
        }
        List<ItemStack> out = new ArrayList<>();
        for (Item item : items) {
            out.add(new ItemStack(item));
        }
        return out;
    }

    @Override
    public List<ItemStack> gridItems(ItemStack target, boolean usage) {
        return target != null && !target.isEmpty() && hasInfo(target)
                ? List.of(target)
                : List.of();
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return hasInfo(target) ? 0 : -1;
    }

    /** Whether any info recipe matches {@code target} (by item). */
    public boolean hasInfo(ItemStack target) {
        if (target == null || target.isEmpty()) return false;
        for (Object recipe : infoRecipes()) {
            for (Item item : ingredientsOf(recipe)) {
                if (item == target.getItem()) return true;
            }
        }
        return false;
    }

    /** All info descriptions covering {@code target}, in registration order. */
    public List<FormattedText> descriptionFor(ItemStack target) {
        List<FormattedText> out = new ArrayList<>();
        if (target == null || target.isEmpty()) return out;
        for (Object recipe : infoRecipes()) {
            if (ingredientsOf(recipe).contains(target.getItem())) {
                try {
                    Object description = get(recipe, "getDescription");
                    if (description instanceof List<?> lines) {
                        for (Object line : lines) {
                            if (line instanceof FormattedText text) out.add(text);
                        }
                    }
                } catch (Exception e) {
                    // broken recipe — skip
                }
            }
        }
        return out;
    }

    // ── JEI 信息配方收集（反射；无 JEI → 空 → 类别缺席） ─────────────────────

    private static Object cachedManager;
    private static List<Object> cachedRecipes;

    private static List<Object> infoRecipes() {
        Object manager = reflectRecipeManager();
        if (manager == null) return List.of();
        if (manager != cachedManager || cachedRecipes == null) {
            cachedRecipes = collect(manager);
            cachedManager = manager;
        }
        return List.copyOf(cachedRecipes);
    }

    /** Reflect headless-jei's JeiRuntimeBridge.recipeManager() (JIJ mod jar;
     *  real JEI present → the bridge still routes to the real runtime). */
    private static Object reflectRecipeManager() {
        try {
            Class<?> bridge = Class.forName("com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge");
            return bridge.getMethod("recipeManager").invoke(null);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static List<Object> collect(Object manager) {
        try {
            Object information = Class.forName("mezz.jei.api.constants.RecipeTypes")
                    .getField("INFORMATION").get(null);
            Object lookup = manager.getClass().getMethod("createRecipeLookup", Object.class)
                    .invoke(manager, information);
            if (lookup == null) return List.of();
            Object iterable = lookup.getClass().getMethod("get").invoke(lookup);
            if (iterable instanceof Iterable<?> it) {
                List<Object> out = new ArrayList<>();
                for (Object recipe : it) {
                    out.add(recipe);
                }
                return out;
            }
            return List.of();
        } catch (Exception | LinkageError e) {
            return List.of();
        }
    }

    private static List<Item> ingredientsOf(Object recipe) {
        List<Item> out = new ArrayList<>();
        try {
            Object ingredients = get(recipe, "getIngredients");
            if (ingredients instanceof List<?> list) {
                for (Object ingredient : list) {
                    Object stack = get(ingredient, "getIngredient");
                    if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                        out.add(itemStack.getItem());
                    } else if (stack instanceof Item item) {
                        out.add(item);
                    }
                }
            }
        } catch (Exception e) {
            // broken recipe — skip
        }
        return out;
    }

    private static Object get(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
