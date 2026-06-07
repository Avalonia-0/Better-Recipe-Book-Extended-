package com.alonie.brbe.mixins.search;

import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
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
    private void betterRecipeBook$onUpdateCollections(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
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
            // Save the search text and clear the box
            // Vanilla's removeIf will then see an empty search and pass everything
            betterRecipeBook$savedSearchText = text;
            betterRecipeBook$parsedQuery = query;
            searchBox.setValue("");
        }
    }

    /**
     * Stage 2: At the point where the list is passed to RecipeBookPage,
     * apply the advanced search filter if we have a parsed query.
     */
    @ModifyArg(method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"),
            index = 0)
    private List<RecipeCollection> betterRecipeBook$applyAdvancedSearch(List<RecipeCollection> collections) {
        if (betterRecipeBook$parsedQuery == null || minecraft.level == null) {
            return collections;
        }

        SearchCache cache = new SearchCache();
        var displayContext = SlotDisplayContext.fromLevel(minecraft.level);

        List<RecipeCollection> filtered = new ArrayList<>();
        for (RecipeCollection collection : collections) {
            boolean added = false;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                List<ItemStack> results = entry.resultItems(displayContext);
                for (ItemStack result : results) {
                    if (result != null && !result.isEmpty()
                            && betterRecipeBook$parsedQuery.matches(result, cache)) {
                        filtered.add(collection);
                        added = true;
                        break;
                    }
                }
                if (added) break;
            }
        }

        return filtered;
    }

    /**
     * Stage 3: At TAIL, restore the search box text if we cleared it.
     */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void betterRecipeBook$restoreSearchText(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        if (betterRecipeBook$savedSearchText != null && searchBox != null) {
            searchBox.setValue(betterRecipeBook$savedSearchText);
            betterRecipeBook$savedSearchText = null;
            betterRecipeBook$parsedQuery = null;
        }
    }
}
