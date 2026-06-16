package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {

    @Accessor("recipeBookPage")
    RecipeBookPage getRecipeBookPage();

    @Accessor("searchBox")
    EditBox getSearchBox();

    @Accessor("searchBox")
    void setSearchBox(EditBox searchBox);

    @Invoker("isFiltering")
    boolean isFilteringInvoker();

    @Invoker("updateCollections")
    void updateCollectionsInvoker(boolean resetPage, boolean filteringChanged);

    @Accessor("xOffset")
    int getXOffset();

    @Accessor("ghostSlots")
    GhostSlots getGhostSlots();

    @Accessor("SEARCH_HINT")
    static Component getSEARCH_HINT() {
        throw new AssertionError();
    }

    @Accessor("ALL_RECIPES_TOOLTIP")
    static Component getALL_RECIPES_TOOLTIP() {
        throw new AssertionError();
    }

}
