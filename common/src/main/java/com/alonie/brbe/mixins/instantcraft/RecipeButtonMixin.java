package com.alonie.brbe.mixins.instantcraft;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeButton.class)
public class RecipeButtonMixin {

    @Shadow private RecipeCollection collection;

    @Unique private List<RecipeDisplayId> betterRecipeBook$lastClicked;

    @Inject(method = "init", at = @At(value = "HEAD"))
    public void init(RecipeCollection collection, boolean filteringCraftable, RecipeBookPage recipeBookPage, ContextMap contextMap, CallbackInfo ci) {
        if (BetterRecipeBook.instantCraftingManager.lastHoveredCollection == collection) {
            BetterRecipeBook.instantCraftingManager.lastHoveredCollection = null;
            betterRecipeBook$lastClicked = List.of(BetterRecipeBook.instantCraftingManager.getLastClickedRecipe());
        }
    }

}
