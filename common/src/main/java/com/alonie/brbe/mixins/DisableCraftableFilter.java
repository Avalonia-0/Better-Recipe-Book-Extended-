package com.alonie.brbe.mixins;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the "Only show craftable" filter button from the recipe book
 * and makes isFiltering() always return false.
 *
 * This ensures the crafting recipe book always shows all recipes
 * (both craftable and uncraftable), matching BRBE's design where
 * partial-material and incompatible recipes should remain visible.
 */
@Mixin(RecipeBookComponent.class)
public abstract class DisableCraftableFilter {

    @Shadow
    protected CycleButton<Boolean> filterButton;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void betterRecipeBook$hideFilterButton(CallbackInfo ci) {
        this.filterButton.visible = false;
        this.filterButton.active = false;
    }

    @Inject(method = "isFiltering", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$disableFiltering(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
