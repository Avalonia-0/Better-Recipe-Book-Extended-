package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Root configuration for Better Recipe Book Extended.
 *
 * <p>Sub-configs ({@code NewRecipes}, {@code InstantCraft},
 * {@code AlternativeRecipes}, {@code Scrolling}) are standalone classes in the
 * same package — same structure as the 1.21.1 branch.  Only
 * {@code RecipeBookIsPain} is nested here (matches 1.21.1's {@code Config}).</p>
 */
@Config(name = "brbe")
public class BrbeConfig implements ConfigData {

    // -- 配方书设置（general 标签）--------------------------------------------

    @ConfigEntry.Gui.Tooltip
    public boolean showModName = false;

    @ConfigEntry.Gui.TransitiveObject
    public Scrolling scrolling = new Scrolling();

    @ConfigEntry.Gui.TransitiveObject
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    @ConfigEntry.Gui.TransitiveObject
    public InstantCraft instantCraft = new InstantCraft();

    // -- 界面设置（ui 标签）----------------------------------------------------

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean keepCentered = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Excluded
    @ConfigEntry.Gui.Tooltip
    public boolean expandedRecipeBook = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean hideReiJeiOverlay = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean settingsButton = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBook = true;

    // -- 配方设置（recipeSettings 标签）----------------------------------------

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    public boolean showAllRecipesInSurvival = true;

    @ConfigEntry.Category("recipeSettings")
    public boolean hideIncompatibleMark = false;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip
    public boolean partialCraftingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean partialMarkingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip
    public boolean partialOnlyWhenCarrying = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.TransitiveObject
    public NewRecipes newRecipes = new NewRecipes();

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.TransitiveObject
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    // -- Inner config class ---------------------------------------------------

    public static class RecipeBookIsPain implements ConfigData {
        @ConfigEntry.Gui.PrefixText
        @ConfigEntry.Gui.Tooltip
        public boolean enableRecipeBookIsPain = true;

        public boolean enableTabPage = true;
    }

    @Override
    public void validatePostLoad() {
        this.expandedRecipeBook = false;
    }
}
