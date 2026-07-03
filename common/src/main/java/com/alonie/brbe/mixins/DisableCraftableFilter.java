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
 * Handles the "alternative" / remove-filter-button mode (§2.10).
 *
 * <p>When {@code partialCraftingEnabled} is on the filter button is
 * hidden and all recipes are shown (sorted craftable→partial→uncraftable).
 * The {@code isFiltering()} override keeps the vanilla filter off so
 * uncraftable recipes survive; the pipeline's sort stage handles the
 * ordering (see {@code pipeline/RecipeBookComponentMixin}).</p>
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

    /**
     * In alternative mode the filter button is hidden — the vanilla
     * filter must also be forced off so uncraftable recipes remain
     * visible.  (Without this, a stale filter-on state from a previous
     * session would silently hide every uncraftable recipe.)
     */
    @Inject(method = "isFiltering", at = @At("RETURN"), cancellable = true)
    private void brbe$disableFiltering(CallbackInfoReturnable<Boolean> cir) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) return;
        cir.setReturnValue(false);
    }
}
