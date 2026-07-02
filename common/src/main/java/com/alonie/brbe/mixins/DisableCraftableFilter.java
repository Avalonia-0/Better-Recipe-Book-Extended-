package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the "Only show craftable" filter button — gated by
 * {@code partialCraftingEnabled}.  The vanilla filtering behaviour is
 * handled by the incompletecrafting mixin (which injects partially-
 * craftable recipes into the craftable set so they survive the
 * vanilla filter).
 */
@Mixin(RecipeBookComponent.class)
public abstract class DisableCraftableFilter {

    @Shadow
    protected CycleButton<Boolean> filterButton;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$hideFilterButton(CallbackInfo ci) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) return;
        this.filterButton.visible = false;
        this.filterButton.active = false;
    }
}
