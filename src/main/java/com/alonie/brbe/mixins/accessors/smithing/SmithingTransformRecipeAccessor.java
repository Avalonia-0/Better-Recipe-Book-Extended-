package com.alonie.brbe.mixins.accessors.smithing;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
    @Accessor("template")
    Optional<Ingredient> getUnderlyingTemplate();

    @Accessor("base")
    Ingredient getUnderlyingBase();

    @Accessor("addition")
    Optional<Ingredient> getUnderlyingAddition();

    @Accessor("result")
    ItemStackTemplate getResult();
}
