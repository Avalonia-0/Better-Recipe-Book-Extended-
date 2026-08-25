package com.alonie.brbe.mixins.localcache;

import com.alonie.brbe.util.RecipeCraftingIndex;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Incremental canCraft: skip {@code selectRecipes} for collections whose
 * ingredients are unaffected by the last inventory change.  The vanilla
 * {@code selectMatchingRecipes()} runs on every recipe-book open AND every
 * inventory change, re-evaluating every recipe's canCraft — O(recipes ×
 * ingredients) even when only one item changed.  The index in
 * {@link RecipeCraftingIndex} knows which collections reference which items;
 * when the changed-item set is empty (open, inventory unchanged) or does not
 * touch a collection's ingredients, its {@code craftable}/{@code selected}
 * sets from the previous pass are still correct and the whole method can be
 * skipped.
 */
@Mixin(RecipeCollection.class)
public abstract class RecipeCollectionMixin {

    @Inject(method = "selectRecipes",
            at = @At("HEAD"),
            cancellable = true)
    private void brbe$skipUnaffectedSelectRecipes(
            StackedItemContents stackedContents,
            Predicate<RecipeDisplay> predicate,
            CallbackInfo ci) {
        RecipeCollection self = (RecipeCollection) (Object) this;
        if (RecipeCraftingIndex.shouldSkip(self)) {
            ci.cancel();
        }
    }

    @Inject(method = "selectRecipes",
            at = @At("TAIL"))
    private void brbe$markSelectRecipesComputed(
            StackedItemContents stackedContents,
            Predicate<RecipeDisplay> predicate,
            CallbackInfo ci) {
        RecipeCraftingIndex.markComputed((RecipeCollection) (Object) this);
    }
}
