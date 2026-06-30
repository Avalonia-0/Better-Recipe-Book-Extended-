package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Expands the vanilla {@link RecipeBookPage} button pool from 20 to 80
 * so the expanded recipe book can fill 4 rows regardless of column count.
 */
@Mixin(RecipeBookPage.class)
public class ExpandedRecipeBookPageMixin {

    /** Buttons to allocate (12 cols × 4 rows). */
    private static final int EXPANDED_BUTTONS = 48;

    /**
     * After vanilla creates 20 buttons, add more so the expanded grid
     * can show 4 full rows.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$addExtraButtons(CallbackInfo ci) {
        if (!BetterRecipeBook.ctx().config().expandedRecipeBook) return;
        List<RecipeButton> buttons = ((RecipeBookPageAccessor) this).getButtons();
        while (buttons.size() < EXPANDED_BUTTONS) {
            buttons.add(new RecipeButton());
        }
    }

    /**
     * Replace the hardcoded 20 in {@code updateButtonsForPage()} with 48
     * (12 cols × 4 rows) so pagination shows exactly 4 rows.
     */
    @ModifyConstant(method = "updateButtonsForPage", constant = @Constant(intValue = 20), require = 0)
    private int brbe$expandPagination(int original) {
        if (BetterRecipeBook.ctx().config().expandedRecipeBook) {
            return EXPANDED_BUTTONS;
        }
        return original;
    }
}
