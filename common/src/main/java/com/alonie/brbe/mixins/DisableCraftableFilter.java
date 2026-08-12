package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.BrbeLogger;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jetbrains.annotations.Nullable;
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

    @Shadow
    private @Nullable EditBox searchBox;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$hideFilterButton(CallbackInfo ci) {
        boolean shouldHide = BetterRecipeBook.ctx().config().partialCraftingEnabled;
        BrbeLogger.log(BrbeLogger.Category.FILTER,
                "DisableCraftableFilter — pCE=%s hiding=%s", shouldHide, shouldHide);
        if (!shouldHide) return;
        this.filterButton.visible = false;
        this.filterButton.active = false;
    }

    /**
     * 过滤按钮被隐藏后，把搜索栏居中放置：左右端距配方书左右端的距离相同。
     * 搜索栏起点固定 xo+25（左端距左缘 25），配方书宽 147 → 居中宽度
     * 147-25*2=97，右端 xo+122（距右缘同为 25）。
     */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$extendSearchBoxToMatchGrid(CallbackInfo ci) {
        if (!BetterRecipeBook.ctx().config().partialCraftingEnabled) return;
        if (this.searchBox == null) return;
        this.searchBox.setWidth(97);
    }
}
