package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.List;

@Mixin(OverlayRecipeComponent.class)
public class OverlayRecipeComponentMixin {
    @Shadow
    @Final
    private List<?> recipeButtons;

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Ljava/util/Collections;emptyList()Ljava/util/List;"))
    private List<RecipeDisplayEntry> betterRecipeBook$showPartiallyCraftableAlternatives(RecipeCollection collection, ContextMap contextMap, boolean isFiltering, int x, int y, int overlayX, int overlayY, float width) {
        if (!BetterRecipeBook.config.partialCraftableEqualsCraftable || !PartialCraftingUtil.wasCheckedForPartialMaterials(collection)) {
            return Collections.emptyList();
        }

        return PartialCraftingUtil.getPartiallyCraftableRecipes(collection);
    }

    @ModifyVariable(method = "init", index = 13, at = @At("STORE"))
    private int betterRecipeBook$expandColumnsAfterFiveRows(int columns, RecipeCollection collection, ContextMap contextMap, boolean isFiltering, int x, int y, int overlayX, int overlayY, float width) {
        int recipeCount = collection.getSelectedRecipes(RecipeCollection.CraftableStatus.CRAFTABLE).size();
        if (!isFiltering) {
            recipeCount += collection.getSelectedRecipes(RecipeCollection.CraftableStatus.NOT_CRAFTABLE).size();
        } else if (BetterRecipeBook.config.partialCraftableEqualsCraftable && PartialCraftingUtil.wasCheckedForPartialMaterials(collection)) {
            recipeCount += PartialCraftingUtil.getPartiallyCraftableRecipes(collection).size();
        }

        return AlternativeOverlayLayout.columnsFor(recipeCount);
    }

    @ModifyVariable(method = "extractRenderState", index = 5, at = @At("STORE"))
    private int betterRecipeBook$renderExpandedColumnsAfterFiveRows(int columns) {
        return AlternativeOverlayLayout.columnsFor(this.recipeButtons.size());
    }
}
