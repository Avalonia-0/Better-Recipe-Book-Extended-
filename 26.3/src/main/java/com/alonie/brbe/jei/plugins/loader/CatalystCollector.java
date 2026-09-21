package com.alonie.brbe.jei.plugins.loader;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.ItemLike;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link IRecipeCatalystRegistration} implementation that records every
 *  workstation association ({@code recipeType -> item}) reported by the loaded
 *  JEI plugins, instead of registering them with a JEI runtime.  Only item
 *  ingredients are collected — workstation blocks are always items. */
public final class CatalystCollector implements IRecipeCatalystRegistration {

    private final Map<Identifier, Set<Identifier>> collected = new LinkedHashMap<>();

    public Map<Identifier, Set<Identifier>> collected() {
        return collected;
    }

    @Override
    public void addCraftingStation(IRecipeType<?> recipeType, ItemLike... ingredients) {
        if (ingredients == null) return;
        for (ItemLike itemLike : ingredients) {
            if (itemLike != null && itemLike.asItem() != null) {
                add(recipeType, itemLike.asItem());
            }
        }
    }

    /** 26.3: {@link IRecipeCatalystRegistration} 新增的"由一个
     *  {@link SlotDisplay} 解析出的物品集合组成一个旋转工作站槽"重载
     *  （{@code @since 30.32.0}）。无头场景只关心"哪些物品是工作站"，因此
     *  把 display 解析出的所有物品逐个并入该类型的物品集合——语义与
     *  {@code addCraftingStation(type, ItemLike...)} 一致（每个物品都能作为
     *  该类型的工作站被查到）。 */
    @Override
    public void addCraftingStation(IRecipeType<?> recipeType, SlotDisplay slotDisplay) {
        if (recipeType == null || slotDisplay == null) return;
        for (ItemStack stack : resolveSlotDisplay(slotDisplay)) {
            if (!stack.isEmpty()) add(recipeType, stack.getItem());
        }
    }

    /** 用客户端等级构建的 {@code ContextMap} 把 {@link SlotDisplay} 展开为物品
     *  （{@code resolveForStacks} 是 vanilla 的默认方法；无等级/解析失败返回
     *  空表，不抛异常）。 */
    private static List<ItemStack> resolveSlotDisplay(SlotDisplay slotDisplay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return List.of();
        try {
            return slotDisplay.resolveForStacks(SlotDisplayContext.fromLevel(minecraft.level));
        } catch (Throwable t) {
            return List.of();
        }
    }

    @Override
    public <T> void addCraftingStation(IRecipeType<?> recipeType, IIngredientType<T> ingredientType, T ingredient) {
        addTyped(recipeType, ingredientType, ingredient);
    }

    @Override
    public <T> void addCraftingStations(IRecipeType<?> recipeType, IIngredientType<T> ingredientType, List<T> ingredients) {
        if (ingredients == null) return;
        for (T ingredient : ingredients) {
            addTyped(recipeType, ingredientType, ingredient);
        }
    }

    @Override
    public <T> void addRecipeCatalyst(IIngredientType<T> ingredientType, T ingredient, IRecipeType<?>... recipeTypes) {
        if (recipeTypes == null) return;
        for (IRecipeType<?> recipeType : recipeTypes) {
            addTyped(recipeType, ingredientType, ingredient);
        }
    }

    @Override
    public IIngredientManager getIngredientManager() {
        return null;
    }

    @Override
    public IJeiHelpers getJeiHelpers() {
        return null;
    }

    private <T> void addTyped(IRecipeType<?> recipeType, IIngredientType<T> ingredientType, T ingredient) {
        if (recipeType == null || ingredient == null) return;
        if (ingredientType != VanillaTypes.ITEM_STACK) return;
        Item item = ((ItemStack) ingredient).getItem();
        if (item != null) add(recipeType, item);
    }

    private void add(IRecipeType<?> recipeType, Item item) {
        if (recipeType == null || item == null) return;
        Identifier uid = recipeType.getUid();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (uid == null || itemId == null) return;
        collected.computeIfAbsent(uid, k -> new LinkedHashSet<>()).add(itemId);
    }
}
