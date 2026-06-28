package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Makes the vanilla {@link RecipeBookPage} button-per-page count dynamic
 * so expanded mode shows more recipes per page instead of a fixed 20.
 */
@Mixin(RecipeBookPage.class)
public class ExpandedRecipeBookPageMixin {

    /**
     * Replace the hardcoded {@code 20} in {@code updateButtonsForPage()}
     * with a dynamic value when the expanded book is active.
     */
    @ModifyConstant(method = "updateButtonsForPage", constant = @Constant(intValue = 20), require = 0)
    private int brbe$dynamicButtonsPerPage(int original) {
        if (BetterRecipeBook.config.expandedRecipeBook) {
            return 80; // plenty of slots, actual count gated by visible=true/false
        }
        return original;
    }
}
