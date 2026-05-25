package com.alonie.brbe.recipe.smithing;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.ItemLike;

import java.util.List;

public class BRBSmithingTransformRecipe implements BRBSmithingRecipe {
    private final Identifier id;
    private final Ingredient template;
    private final ItemStack base;
    private final Ingredient addition;
    private final ItemStack result;
    private final boolean requiresTemplate;
    private final boolean requiresAddition;

    private BRBSmithingTransformRecipe(Identifier id, Ingredient template, ItemStack base, Ingredient addition, ItemStack result, boolean requiresTemplate, boolean requiresAddition) {
        this.id = id;
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
        this.requiresTemplate = requiresTemplate;
        this.requiresAddition = requiresAddition;
    }

    public static BRBSmithingTransformRecipe from(RecipeDisplayEntry entry, SmithingRecipeDisplay display, ContextMap displayContext) {
        List<ItemStack> templateStacks = display.template().resolveForStacks(displayContext);
        List<ItemStack> additionStacks = display.addition().resolveForStacks(displayContext);
        Ingredient template = ingredientFrom(templateStacks);
        ItemStack base = firstResolved(display.base().resolveForStacks(displayContext));
        Ingredient addition = ingredientFrom(additionStacks);
        ItemStack result = firstResolved(entry.resultItems(displayContext));
        Identifier id = buildId("transform", template, base, addition, result);
        return new BRBSmithingTransformRecipe(id, template, base, addition, result, !templateStacks.isEmpty(), !additionStacks.isEmpty());
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category) {
        return this.result.copy();
    }

    @Override
    public ItemStack getResult(ResourceKey<TrimMaterial> trimMaterialResourceKey, RegistryAccess registryAccess, BRBBookCategories.Category category) {
        return this.getResult(registryAccess, category);
    }

    @Override
    public Ingredient getTemplate() {
        return this.template;
    }

    @Override
    public ItemStack getBase() {
        return this.base.copy();
    }

    @Override
    public Ingredient getAddition() {
        return this.addition;
    }

    @Override
    public boolean requiresTemplate() {
        return this.requiresTemplate;
    }

    @Override
    public boolean requiresAddition() {
        return this.requiresAddition;
    }

    private static Ingredient ingredientFrom(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return Ingredient.of(Items.BARRIER);
        }

        return Ingredient.of(stacks.stream().map(ItemStack::getItem));
    }

    private static ItemStack firstResolved(List<ItemStack> stacks) {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }

    private static Identifier buildId(String kind, Ingredient template, ItemStack base, Ingredient addition, ItemStack result) {
        return Identifier.fromNamespaceAndPath(BetterRecipeBook.MOD_ID,
                "smithing/" + kind + "/"
                        + keyPart(firstItem(template)) + "/"
                        + keyPart(base) + "/"
                        + keyPart(firstItem(addition)) + "/"
                        + keyPart(result));
    }

    private static ItemStack firstItem(Ingredient ingredient) {
        return ingredient.items()
                .findFirst()
                .map(holder -> holder.value().getDefaultInstance())
                .orElse(ItemStack.EMPTY);
    }

    private static String keyPart(ItemStack stack) {
        ItemLike item = stack.isEmpty() ? net.minecraft.world.item.Items.AIR : stack.getItem();
        Identifier key = BuiltInRegistries.ITEM.getKey(item.asItem());
        return key.getNamespace() + "_" + key.getPath();
    }
}
