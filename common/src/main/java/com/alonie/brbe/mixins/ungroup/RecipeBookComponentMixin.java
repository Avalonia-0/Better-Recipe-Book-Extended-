package com.alonie.brbe.mixins.ungroup;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {
    @ModifyArg(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"), index = 0)
    private List<RecipeCollection> splitCollectionsAfterFiltering(List<RecipeCollection> collections) {
        if (!BetterRecipeBook.config.alternativeRecipes.noGrouped) {
            return collections;
        }

        List<RecipeCollection> splitCollections = new ArrayList<>(collections.size());
        for (RecipeCollection collection : collections) {
            List<RecipeDisplayEntry> recipes = collection.getRecipes();
            if (recipes.size() <= 1) {
                splitCollections.add(collection);
                continue;
            }

            RecipeCollectionAccessor source = (RecipeCollectionAccessor) collection;
            boolean restrictToCraftableOrPartial = PartialCraftingUtil.hasPartialMaterials(collection)
                    || collection.hasCraftable();
            boolean addedAny = false;
            for (RecipeDisplayEntry recipe : recipes) {
                if (!source.betterRecipeBook$getSelected().contains(recipe.id())) {
                    continue;
                }

                boolean isCraftable = source.betterRecipeBook$getCraftable().contains(recipe.id());
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftable(collection, recipe.id());
                if (restrictToCraftableOrPartial && !isCraftable && !isPartial) {
                    continue;
                }

                RecipeCollection splitCollection = new RecipeCollection(Collections.singletonList(recipe));
                RecipeCollectionAccessor split = (RecipeCollectionAccessor) splitCollection;
                split.betterRecipeBook$getSelected().add(recipe.id());
                if (isCraftable) {
                    split.betterRecipeBook$getCraftable().add(recipe.id());
                }
                if (isPartial) {
                    PartialCraftingUtil.markPartialMaterial(splitCollection, recipe.id());
                }

                splitCollections.add(splitCollection);
                addedAny = true;
            }

            if (!addedAny && !restrictToCraftableOrPartial) {
                splitCollections.add(collection);
            }
        }

        return splitCollections;
    }
}
