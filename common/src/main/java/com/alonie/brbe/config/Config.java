package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@me.shedaniel.autoconfig.annotation.Config(name = "brbe")
public class Config implements ConfigData {

    // -- 配方书设置 ------------------------------------------------------------

    @ConfigEntry.Gui.Tooltip()
    public boolean showModName = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean scrollAround = false;

    @ConfigEntry.Gui.TransitiveObject()
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    @ConfigEntry.Gui.TransitiveObject()
    public InstantCraft instantCraft = new InstantCraft();

    // -- 界面设置 --------------------------------------------------------------

    @ConfigEntry.Category("ui")
    public boolean keepCentered = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Excluded
    @ConfigEntry.Gui.Tooltip()
    public boolean expandedRecipeBook = false;

    @ConfigEntry.Category("ui")
    public boolean hideReiJeiOverlay = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText()
    public boolean settingsButton = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip()
    public boolean enableBook = true;

    // -- 配方设置 --------------------------------------------------------------

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText()
    public boolean showAllRecipesInSurvival = true;

    @ConfigEntry.Category("recipeSettings")
    public boolean hideIncompatibleMark = false;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip()
    public boolean partialCraftingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText()
    @ConfigEntry.Gui.Tooltip()
    public boolean partialMarkingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip()
    public boolean partialOnlyWhenCarrying = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText()
    @ConfigEntry.Gui.TransitiveObject()
    public NewRecipes newRecipes = new NewRecipes();

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText()
    @ConfigEntry.Gui.TransitiveObject()
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    public static class RecipeBookIsPain {
        @ConfigEntry.Gui.PrefixText()
        @ConfigEntry.Gui.Tooltip()
        public boolean enableRecipeBookIsPain = true;

        public boolean enableTabPage = true;
    }

    @Override
    public void validatePostLoad() {
        this.expandedRecipeBook = false;
    }
}
