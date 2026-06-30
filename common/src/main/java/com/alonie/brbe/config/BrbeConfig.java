package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Root configuration for Better Recipe Book Extended.
 *
 * <p>This replaces the old {@code Config} class.  The Cloth Config annotation
 * structure is preserved so that the TOML serialization format stays
 * backward-compatible with existing {@code brbe.toml} files.</p>
 *
 * <p>Unlike the old design, this config object is an <em>immutable snapshot</em>
 * held by {@link AppContext}.  Modules receive it via constructor injection.
 * When the user saves new settings, a fresh {@code BrbeConfig} is published
 * through {@link ConfigEventBus}.</p>
 */
@Config(name = "brbe")
public class BrbeConfig implements ConfigData {

    // -- Top-level toggles ----------------------------------------------------

    @ConfigEntry.Gui.Tooltip
    public boolean enablePinning = true;

    @ConfigEntry.Gui.Tooltip
    public boolean settingsButton = true;

    @ConfigEntry.Gui.Tooltip
    public boolean enableBook = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showModName = false;

    @ConfigEntry.Gui.Tooltip
    public boolean hideReiJeiOverlay = false;

    // -- UI -------------------------------------------------------------------

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean keepCentered = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean expandedRecipeBook = false;

    // -- Recipe filter --------------------------------------------------------

    @ConfigEntry.Category("recipeFilter")
    @ConfigEntry.Gui.Tooltip
    public boolean showAllRecipesInSurvival = true;

    @ConfigEntry.Category("recipeFilter")
    @ConfigEntry.Gui.Tooltip
    public boolean partialCraftingEnabled = true;

    @ConfigEntry.Category("recipeFilter")
    @ConfigEntry.Gui.Tooltip
    public boolean partialMarkingEnabled = true;

    // -- RBIP -----------------------------------------------------------------

    @ConfigEntry.Category("rbip")
    @ConfigEntry.Gui.TransitiveObject
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    // -- Sub-configs ----------------------------------------------------------

    @ConfigEntry.Category("newRecipes")
    @ConfigEntry.Gui.TransitiveObject
    public NewRecipes newRecipes = new NewRecipes();

    @ConfigEntry.Category("instantCraft")
    @ConfigEntry.Gui.TransitiveObject
    public InstantCraft instantCraft = new InstantCraft();

    @ConfigEntry.Category("alternativeRecipes")
    @ConfigEntry.Gui.TransitiveObject
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    @ConfigEntry.Category("scrolling")
    @ConfigEntry.Gui.TransitiveObject
    public Scrolling scrolling = new Scrolling();

    // -- Inner config classes -------------------------------------------------

    public static class RecipeBookIsPain implements ConfigData {
        @ConfigEntry.Gui.Excluded
        public boolean enableRecipeBookIsPain = true;

        @ConfigEntry.Gui.Excluded
        public boolean enableTabPage = true;
    }

    public static class NewRecipes implements ConfigData {
        @ConfigEntry.Gui.Tooltip
        public boolean unlockAll = true;

        @ConfigEntry.Gui.Tooltip
        public boolean enableBounce = false;
    }

    public static class InstantCraft implements ConfigData {
        @ConfigEntry.Gui.Tooltip
        public boolean showButton = true;

        @ConfigEntry.Gui.Tooltip
        public boolean enabled = false;
    }

    public static class AlternativeRecipes implements ConfigData {
        @ConfigEntry.Gui.Tooltip
        public boolean onHover = true;

        @ConfigEntry.Gui.Tooltip
        public boolean noGrouped = false;
    }

    public static class Scrolling implements ConfigData {
        @ConfigEntry.Gui.Tooltip
        public boolean enableScrolling = true;

        @ConfigEntry.Gui.Tooltip
        public boolean scrollAround = false;
    }
}
