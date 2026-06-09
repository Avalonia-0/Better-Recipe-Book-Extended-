package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures incompatible (3×3) recipes are included in the display list
 * when showAllRecipesInSurvival is enabled on the inventory screen.
 *
 * Uses the public API {@link RecipeCollection#getDisplayRecipes(boolean)}
 * instead of targeting private fields or methods on RecipeButton.
 */
@Mixin(RecipeCollection.class)
public abstract class RecipeCollectionMixin {

    @Inject(method = "getDisplayRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$includeIncompatibleRecipes(
            boolean craftableOnly, CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<RecipeHolder<?>> recipes = cir.getReturnValue();
        if (recipes == null) return;

        RecipeCollection self = (RecipeCollection) (Object) this;

        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : self.getRecipes()) {
            if (IncompatibleCraftingUtil.checkIncompatible(self, holder.id())
                    && !recipes.contains(holder)) {
                if (extras == null) extras = new ArrayList<>();
                extras.add(holder);
            }
        }

        if (extras != null) {
            List<RecipeHolder<?>> combined = new ArrayList<>(recipes);
            combined.addAll(extras);
            cir.setReturnValue(combined);
        }
    }
}
