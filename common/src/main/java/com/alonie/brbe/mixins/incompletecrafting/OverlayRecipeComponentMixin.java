package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.OverlayRecipeCollectionHolder;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(OverlayRecipeComponent.class)
public class OverlayRecipeComponentMixin {
    @Shadow
    @Final
    private List<?> recipeButtons;

    /**
     * Captures the current RecipeCollection so inner buttons can check partial
     * craftability without accessing {@code this$0} (fails on Fabric).
     */
    @Inject(method = "init", at = @At("HEAD"))
    private void rbip$captureCollection(RecipeCollection collection, ContextMap contextMap,
                                         boolean isFiltering, int x, int y, int overlayX,
                                         int overlayY, float width, CallbackInfo ci) {
        OverlayRecipeCollectionHolder.set(collection);
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Ljava/util/Collections;emptyList()Ljava/util/List;"))
    private List<RecipeDisplayEntry> brbe$showPartiallyCraftableAlternatives(RecipeCollection collection, ContextMap contextMap, boolean isFiltering, int x, int y, int overlayX, int overlayY, float width) {
        return PartialCraftingUtil.getPartiallyCraftableRecipes(collection);
    }

    // Replace fragile @ModifyVariable(index=13) with @Redirect on Math.ceilDiv
    // which computes the column count in OverlayRecipeComponent.init().
    // The vanilla code does: columns = Math.min(5, Math.ceilDiv(recipeCount, 5))
    // We redirect ceilDiv to use our expanded recipe count (including partials).
    @Redirect(method = "init",
              at = @At(value = "INVOKE", target = "Ljava/lang/Math;ceilDiv(II)I"))
    private int brbe$expandColumns(int dividend, int divisor,
                                    RecipeCollection collection, ContextMap contextMap,
                                    boolean isFiltering, int x, int y, int overlayX,
                                    int overlayY, float width) {
        int recipeCount = collection.getSelectedRecipes(RecipeCollection.CraftableStatus.CRAFTABLE).size();
        if (!isFiltering) {
            recipeCount += collection.getSelectedRecipes(RecipeCollection.CraftableStatus.NOT_CRAFTABLE).size();
        } else {
            recipeCount += PartialCraftingUtil.getPartiallyCraftableRecipes(collection).size();
        }
        return Math.ceilDiv(Math.max(recipeCount, dividend), divisor);
    }

    @ModifyVariable(method = "render", index = 5, at = @At("STORE"))
    private int brbe$renderExpandedColumnsAfterFiveRows(int columns) {
        return AlternativeOverlayLayout.columnsFor(this.recipeButtons.size());
    }
}
