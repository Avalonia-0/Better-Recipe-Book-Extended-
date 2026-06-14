package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the "Only show craftable" filter button and disables the
 * vanilla filtering behaviour — gated by {@code partialCraftingEnabled}.
 */
@Mixin(RecipeBookComponent.class)
public abstract class DisableCraftableFilter {

    @Shadow
    protected CycleButton<Boolean> filterButton;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void betterRecipeBook$hideFilterButton(CallbackInfo ci) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) return;
        this.filterButton.visible = false;
        this.filterButton.active = false;
    }

    @Inject(method = "isFiltering", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$disableFiltering(CallbackInfoReturnable<Boolean> cir) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) return;
        cir.setReturnValue(false);
    }
}
