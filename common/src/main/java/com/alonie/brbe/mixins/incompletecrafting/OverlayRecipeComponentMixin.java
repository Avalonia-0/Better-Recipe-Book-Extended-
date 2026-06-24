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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Enhances the alternative-recipe overlay with partially-craftable recipes.
 *
 * <p>Uses {@link ModifyVariable} on the list and columns local variables
 * rather than {@code @Redirect} on JDK methods such as
 * {@code Collections.emptyList()}, which can behave inconsistently across
 * Mixin implementations (Fabric vs. NeoForge).
 */
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

    /**
     * Intercepts the list variable right after it is stored (local-var index 10,
     * first store).  When filtering is active the vanilla code puts
     * {@code Collections.emptyList()} there — replace it with the partially-
     * craftable recipes so the overlay actually shows something.
     *
     * <p>Previous approach used {@code @Redirect(Collections.emptyList())}
     * which failed to match on Fabric's Mixin implementation.
     */
    @ModifyVariable(method = "init", index = 10, at = @At(value = "STORE", ordinal = 0))
    private List<RecipeDisplayEntry> brbe$injectPartialRecipes(
            List<RecipeDisplayEntry> original,
            RecipeCollection collection,
            ContextMap contextMap,
            boolean isFiltering,
            int x, int y, int overlayX, int overlayY, float width) {

        if (isFiltering) {
            List<RecipeDisplayEntry> partials = PartialCraftingUtil.getPartiallyCraftableRecipes(collection);
            if (!partials.isEmpty()) {
                return partials;
            }
        }
        return original;
    }

    @ModifyVariable(method = "init", index = 13, at = @At("STORE"))
    private int brbe$expandColumnsAfterFiveRows(int columns, RecipeCollection collection,
                                                 ContextMap contextMap, boolean isFiltering,
                                                 int x, int y, int overlayX, int overlayY,
                                                 float width) {
        int recipeCount = collection.getSelectedRecipes(RecipeCollection.CraftableStatus.CRAFTABLE).size();
        if (!isFiltering) {
            recipeCount += collection.getSelectedRecipes(RecipeCollection.CraftableStatus.NOT_CRAFTABLE).size();
        } else {
            recipeCount += PartialCraftingUtil.getPartiallyCraftableRecipes(collection).size();
        }

        return AlternativeOverlayLayout.columnsFor(recipeCount);
    }

    @ModifyVariable(method = "render", index = 5, at = @At("STORE"))
    private int brbe$renderExpandedColumnsAfterFiveRows(int columns) {
        return AlternativeOverlayLayout.columnsFor(this.recipeButtons.size());
    }
}
