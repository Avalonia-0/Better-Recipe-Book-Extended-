package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

/**
 * After updateCollections() runs its internal forEach consumer (which populates
 * the fitsDimensions set), adds incompatible (3x3) recipes to fitsDimensions
 * so getRecipes(false) and getOrderedRecipes() naturally return them.
 *
 * This is the cleanest fix: rather than intercepting removeIf or patching
 * return values at every call site, we inject incompatible recipes directly
 * into the data set that controls what getRecipes(boolean) returns.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected Minecraft minecraft;

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void betterRecipeBook$addIncompatibleToFitsDimensions(
            List<RecipeCollection> collections, Consumer<? super RecipeCollection> consumer) {
        // Run original forEach first (populates craftable + fitsDimensions sets)
        collections.forEach(consumer);
        // Now add incompatible (3x3) recipes to fitsDimensions
        if (BetterRecipeBook.config.showAllRecipesInSurvival
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }
    }
}
