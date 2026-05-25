package com.alonie.brbe.recipe.smithing;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

public class BRBSmithingTrimRecipe implements BRBSmithingRecipe {
    private final Identifier id;
    private final Ingredient template;
    private final Ingredient addition;
    private final ItemStack itemStackBase;
    private final Holder<TrimPattern> pattern;

    private BRBSmithingTrimRecipe(Identifier id, Ingredient template, Ingredient addition, ItemStack itemStackBase, Holder<TrimPattern> pattern) {
        this.id = id;
        this.template = template;
        this.addition = addition;
        this.itemStackBase = itemStackBase;
        this.pattern = pattern;
    }

    public static ArrayList<BRBSmithingTrimRecipe> from(SmithingRecipeDisplay display, ContextMap displayContext) {
        ArrayList<BRBSmithingTrimRecipe> results = new ArrayList<>();
        if (!(display.result() instanceof SlotDisplay.SmithingTrimDemoSlotDisplay trimDisplay)) {
            return results;
        }

        Ingredient template = ingredientFrom(display.template().resolveForStacks(displayContext));
        Ingredient addition = ingredientFrom(display.addition().resolveForStacks(displayContext));

        for (ItemStack base : display.base().resolveForStacks(displayContext)) {
            if (base.isEmpty()) {
                continue;
            }

            Identifier id = buildId(template, base, addition);
            results.add(new BRBSmithingTrimRecipe(id, template, addition, base, trimDisplay.pattern()));
        }

        return results;
    }

    @Override
    public Identifier id() {
        return this.id;
    }

    @Override
    public ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category) {
        return this.getResult(TrimMaterials.REDSTONE, registryAccess, category);
    }

    @Override
    public ItemStack getResult(ResourceKey<TrimMaterial> trimMaterialResourceKey, RegistryAccess registryAccess, BRBBookCategories.Category category) {
        return SmithingTrimRecipe.applyTrim(registryAccess, this.itemStackBase.copy(), this.materialStack(trimMaterialResourceKey), this.pattern);
    }

    private ItemStack materialStack(ResourceKey<TrimMaterial> material) {
        if (material == TrimMaterials.QUARTZ) return new ItemStack(Items.QUARTZ);
        if (material == TrimMaterials.IRON) return new ItemStack(Items.IRON_INGOT);
        if (material == TrimMaterials.NETHERITE) return new ItemStack(Items.NETHERITE_INGOT);
        if (material == TrimMaterials.REDSTONE) return new ItemStack(Items.REDSTONE);
        if (material == TrimMaterials.COPPER) return new ItemStack(Items.COPPER_INGOT);
        if (material == TrimMaterials.GOLD) return new ItemStack(Items.GOLD_INGOT);
        if (material == TrimMaterials.EMERALD) return new ItemStack(Items.EMERALD);
        if (material == TrimMaterials.DIAMOND) return new ItemStack(Items.DIAMOND);
        if (material == TrimMaterials.LAPIS) return new ItemStack(Items.LAPIS_LAZULI);
        if (material == TrimMaterials.AMETHYST) return new ItemStack(Items.AMETHYST_SHARD);
        if (material == TrimMaterials.RESIN) return new ItemStack(Items.RESIN_BRICK);
        return ClientCompat.firstIngredientItem(this.getAddition());
    }

    @Override
    public ItemStack getBase() {
        return this.itemStackBase.copy();
    }

    @Override
    public Ingredient getTemplate() {
        return this.template;
    }

    @Override
    public Ingredient getAddition() {
        return this.addition;
    }

    private static Ingredient ingredientFrom(List<ItemStack> stacks) {
        return Ingredient.of(stacks.stream().map(ItemStack::getItem));
    }

    private static Identifier buildId(Ingredient template, ItemStack base, Ingredient addition) {
        return Identifier.fromNamespaceAndPath(BetterRecipeBook.MOD_ID,
                "smithing/trim/"
                        + keyPart(firstItem(template)) + "/"
                        + keyPart(base) + "/"
                        + keyPart(firstItem(addition)));
    }

    private static ItemStack firstItem(Ingredient ingredient) {
        return ingredient.items()
                .findFirst()
                .map(holder -> holder.value().getDefaultInstance())
                .orElse(ItemStack.EMPTY);
    }

    private static String keyPart(ItemStack stack) {
        ItemLike item = stack.isEmpty() ? Items.AIR : stack.getItem();
        Identifier key = BuiltInRegistries.ITEM.getKey(item.asItem());
        return key.getNamespace() + "_" + key.getPath();
    }
}
