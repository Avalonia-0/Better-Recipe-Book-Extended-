package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the "Only show craftable" filter button — gated by partialCraftingEnabled.
 *
 * 1.21.1 uses StateSwitchingButton (not CycleButton). There is no
 * isFiltering() method on RecipeBookComponent — the filtering check
 * is book.isFiltering(menu) on RecipeBook, which is redirected in
 * RecipeButtonMixin.
 */
@Mixin(RecipeBookComponent.class)
public abstract class DisableCraftableFilter {

    @Shadow
    protected StateSwitchingButton filterButton;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$hideFilterButton(CallbackInfo ci) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) return;
        this.filterButton.visible = false;
        this.filterButton.active = false;
    }
}
