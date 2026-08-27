package com.alonie.brbe.mixins.localcache;

import com.alonie.brbe.util.RecipeCraftingIndex;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.entity.player.StackedContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks collections as fully evaluated after vanilla {@code canCraft} runs.
 * Pairs with {@link RecipeCraftingIndex#shouldSkip} in the forEach redirect:
 * a collection whose ingredients are unaffected by the last inventory change
 * is skipped (canCraft not run, craftable set retained), and only collections
 * that actually ran canCraft get the COMPUTED mark — so the incremental index
 * knows which results are fresh.
 */
@Mixin(RecipeCollection.class)
public abstract class RecipeCollectionMixin {

    @Inject(method = "canCraft",
            at = @At("TAIL"))
    private void brbe$markCanCraftComputed(
            StackedContents stackedContents, int gridWidth, int gridHeight,
            RecipeBook recipeBook, CallbackInfo ci) {
        RecipeCraftingIndex.markComputed((RecipeCollection) (Object) this);
    }
}
