package com.alonie.brbe.mixins.search;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced search integration for the vanilla crafting recipe book on 1.21.1.
 * <p>
 * On 1.21.1, updateCollections(boolean) is used (not (boolean, boolean)).
 * Uses @ModifyArg on RecipeBookPage.updateCollections(List, boolean) to
 * filter the recipe list with advanced search syntax.
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    protected EditBox searchBox;

    @Unique
    private String betterRecipeBook$savedSearchText;

    @Unique
    private SearchQuery betterRecipeBook$parsedQuery;

    /**
     * Stage 1: At HEAD, detect advanced search syntax.
     * If found, save and clear the search box so vanilla's substring filter is a no-op.
     */
    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$onUpdateCollections(boolean resetPageNumber, CallbackInfo ci) {
        betterRecipeBook$savedSearchText = null;
        betterRecipeBook$parsedQuery = null;

        if (searchBox == null) {
            return;
        }

        String text = searchBox.getValue();
        if (text == null || text.isEmpty()) {
            return;
        }

        SearchQuery query = SearchQuery.parse(text);
        if (query.isAdvanced()) {
            betterRecipeBook$savedSearchText = text;
            betterRecipeBook$parsedQuery = query;
            searchBox.setValue("");
        }
    }

    /**
     * Stage 2: Filter the recipe list using advanced search.
     */
    @ModifyArg(method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"),
            index = 0)
    private List<RecipeCollection> betterRecipeBook$applyAdvancedSearch(List<RecipeCollection> collections) {
        if (betterRecipeBook$parsedQuery == null || minecraft.level == null) {
            return collections;
        }

        SearchCache cache = new SearchCache();
        var registryAccess = minecraft.level.registryAccess();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection collection : collections) {
            boolean added = false;
            for (RecipeHolder<?> recipe : collection.getRecipes()) {
                ItemStack result = recipe.value().getResultItem(registryAccess);
                if (result != null && !result.isEmpty()
                        && betterRecipeBook$parsedQuery.matches(result, cache)) {
                    filtered.add(collection);
                    added = true;
                    break;
                }
            }
        }

        return filtered;
    }

    /**
     * Stage 3: Restore search box text if it was cleared.
     */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void betterRecipeBook$restoreSearchText(boolean resetPageNumber, CallbackInfo ci) {
        if (betterRecipeBook$savedSearchText != null && searchBox != null) {
            searchBox.setValue(betterRecipeBook$savedSearchText);
            betterRecipeBook$savedSearchText = null;
            betterRecipeBook$parsedQuery = null;
        }
    }
}
