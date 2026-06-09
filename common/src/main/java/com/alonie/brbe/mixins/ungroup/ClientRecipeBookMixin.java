package com.alonie.brbe.mixins.ungroup;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mixin(ClientRecipeBook.class)
public class ClientRecipeBookMixin extends RecipeBook {
    @Shadow private Map<ExtendedRecipeBookCategory, List<RecipeCollection>> collectionsByTab;

    @Inject(method = "getCollection", locals = LocalCapture.CAPTURE_FAILHARD, at = @At("RETURN"), cancellable = true)
    private void split(ExtendedRecipeBookCategory category, CallbackInfoReturnable<List<RecipeCollection>> cir) {
        if (BetterRecipeBook.config.alternativeRecipes.noGrouped) {
            List<RecipeCollection> list = Lists.newArrayList(this.collectionsByTab.getOrDefault(category, Collections.emptyList()));
            List<RecipeCollection> list2 = Lists.newArrayList(list);

            for (RecipeCollection recipeResultCollection : list) {
                if (recipeResultCollection.getRecipes().size() > 1) {
                    List<RecipeDisplayEntry> recipes = recipeResultCollection.getRecipes();
                    list2.remove(recipeResultCollection);

                    for (RecipeDisplayEntry recipe : recipes) {
                        RecipeCollection splitCollection = new RecipeCollection(Collections.singletonList(recipe));
                        RecipeCollectionAccessor sourceAccessor = (RecipeCollectionAccessor) recipeResultCollection;
                        RecipeCollectionAccessor splitAccessor = (RecipeCollectionAccessor) splitCollection;

                        if (sourceAccessor.betterRecipeBook$getSelected().contains(recipe.id())) {
                            splitAccessor.betterRecipeBook$getSelected().add(recipe.id());
                        }
                        if (sourceAccessor.betterRecipeBook$getCraftable().contains(recipe.id())) {
                            splitAccessor.betterRecipeBook$getCraftable().add(recipe.id());
                        }

                        // Transfer incompatible marking to the split collection
                        if (IncompatibleCraftingUtil.isIncompatible(recipeResultCollection, recipe.id())) {
                            IncompatibleCraftingUtil.markIncompatibleOnCollection(splitCollection, recipe.id());
                        }

                        list2.add(splitCollection);
                    }
                }
            }
            cir.setReturnValue(list2);
        }
    }
}
