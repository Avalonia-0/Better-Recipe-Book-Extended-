package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RecipeBookPage.class)
public interface RecipeBookPageAccessor {

    @Accessor("buttons")
    List<RecipeButton> getButtons();

    @Accessor("overlay")
    OverlayRecipeComponent getOverlay();

    @Accessor("recipeCollections")
    List<RecipeCollection> getCollections();

    @Accessor("forwardButton")
    ImageButton getForwardButton();

    @Accessor("backButton")
    ImageButton getBackButton();

    @Accessor("currentPage")
    int getCurrentPage();

    @Accessor("currentPage")
    void setCurrentPage(int currentPage);

    @Accessor("totalPages")
    int getTotalPages();

    @org.spongepowered.asm.mixin.gen.Invoker("updateButtonsForPage")
    void updateButtonsForPageInvoker();

}
