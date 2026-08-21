package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {

    @Accessor("recipeBookPage")
    RecipeBookPage getRecipeBookPage();

    @Accessor("tabButtons")
    List<RecipeBookTabButton> getTabButtons();

    @Accessor("tabInfos")
    List<RecipeBookComponent.TabInfo> getTabInfos();

    @Accessor("selectedTab")
    RecipeBookTabButton getSelectedTab();

    @Accessor("book")
    ClientRecipeBook getBook();

    @Accessor("selectedTab")
    void setSelectedTab(RecipeBookTabButton selectedTab);

    @Invoker("updateTabs")
    void updateTabsInvoker(boolean filteringCraftable);

    @Accessor("searchBox")
    EditBox getSearchBox();

    @Accessor("searchBox")
    void setSearchBox(EditBox searchBox);

    @Invoker("setVisible")
    void setVisibleInvoker(boolean visible);

    @Invoker("toggleVisibility")
    void toggleVisibilityInvoker();

    @Invoker("isFiltering")
    boolean isFilteringInvoker();

    @Invoker("updateCollections")
    void updateCollectionsInvoker(boolean resetPage, boolean filteringChanged);

    @Invoker("initVisuals")
    void initVisualsInvoker();

    @Invoker("updateStackedContents")
    void updateStackedContentsInvoker();

    @Accessor("xOffset")
    int getXOffset();

    @Invoker("getXOrigin")
    int brbe$invokeGetXOrigin();

    @Invoker("getYOrigin")
    int brbe$invokeGetYOrigin();

    @Accessor("ghostSlots")
    GhostSlots getGhostSlots();

    @Invoker("tryPlaceRecipe")
    boolean tryPlaceRecipeInvoker(RecipeCollection collection, RecipeDisplayId id,
                                  boolean hasShiftDown);

    /** Vanilla refuses the first placement of a not-fully-craftable recipe
     *  ({@code !isCraftable && id != lastPlacedRecipe}); the viewer primes this
     *  field so a first click on a partial recipe places its available
     *  materials (the ghost flow) exactly like vanilla's repeat click. */
    @Accessor("lastPlacedRecipe")
    void setLastPlacedRecipe(RecipeDisplayId id);

    @Accessor("lastRecipe")
    void setLastRecipe(RecipeDisplayId lastRecipe);

    @Accessor("lastRecipeCollection")
    void setLastRecipeCollection(RecipeCollection collection);

    @Accessor("SEARCH_HINT")
    static Component getSEARCH_HINT() {
        throw new AssertionError();
    }

    @Accessor("ALL_RECIPES_TOOLTIP")
    static Component getALL_RECIPES_TOOLTIP() {
        throw new AssertionError();
    }

}
