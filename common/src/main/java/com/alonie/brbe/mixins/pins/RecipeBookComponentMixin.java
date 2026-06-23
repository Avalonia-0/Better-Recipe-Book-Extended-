package com.alonie.brbe.mixins.pins;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.interfaces.IPinningComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements IPinningComponent<PinnableRecipeCollection> {

    @Unique
    public void betterRecipeBook$sortByPinsInPlaceCollection(List<RecipeCollection> results) {
        List<RecipeCollection> tempResults = Lists.newArrayList(results);

        if (BetterRecipeBook.config.enablePinning) {
            for (RecipeCollection result : tempResults) {
                if (BetterRecipeBook.pinnedRecipeManager.has(PinnableRecipeCollection.of(result))) {
                    results.remove(result);
                    results.add(0, result);
                }
            }
        }
    }

}
