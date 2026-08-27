package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("ghostRecipe")
    GhostRecipe getGhostRecipe();

    @Accessor("book")
    net.minecraft.client.ClientRecipeBook getRecipeBook();

    @Accessor("recipeBookPage")
    RecipeBookPage getRecipeBookPage();

    @Accessor("searchBox")
    EditBox getSearchBox();

    @Accessor("searchBox")
    void setSearchBox(EditBox searchBox);

    @Accessor("stackedContents")
    StackedContents getStackedContents();

    @Invoker("updateStackedContents")
    void updateStackedContentsInvoker();

    @Invoker("updateScreenPosition")
    int updateScreenPositionInvoker(int width, int backgroundWidth);

    @Invoker("updateCollections")
    void updateCollectionsInvoker(boolean b);

    @Invoker("initVisuals")
    void initVisualsInvoker();

    @Accessor("visible")
    boolean getVisible();

    @Accessor("xOffset")
    int getXOffset();

    @Accessor("filterButton")
    net.minecraft.client.gui.components.StateSwitchingButton getFilterButton();

    @Accessor("selectedTab")
    net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton getSelectedTab();

    @Accessor("selectedTab")
    void setSelectedTab(net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton value);

    @Accessor("tabButtons")
    java.util.List<net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton> getTabButtons();

    @org.spongepowered.asm.mixin.gen.Invoker("updateTabs")
    void updateTabsInvoker();

    @Accessor("SEARCH_HINT")
    static Component getSEARCH_HINT() {
        throw new AssertionError();
    }

    @Accessor("ALL_RECIPES_TOOLTIP")
    static Component getALL_RECIPES_TOOLTIP() {
        throw new AssertionError();
    }
}
