package com.alonie.brbe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@me.shedaniel.autoconfig.annotation.Config(name = "brbe")
public class Config implements ConfigData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enablePinning = true;

    public boolean keepCentered = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean showModName = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean partialCraftableEqualsCraftable = false;

    @ConfigEntry.Gui.Tooltip()
    public boolean hideReiJeiOverlay = false;

    public boolean showAllRecipesInSurvival = true;

    @ConfigEntry.Category("rbip")
    @ConfigEntry.Gui.TransitiveObject()
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    @ConfigEntry.Category("newRecipes")
    @ConfigEntry.Gui.TransitiveObject()
    public NewRecipes newRecipes = new NewRecipes();

    @ConfigEntry.Category("instantCraft")
    @ConfigEntry.Gui.TransitiveObject()
    public InstantCraft instantCraft = new InstantCraft();

    @ConfigEntry.Category("alternativeRecipes")
    @ConfigEntry.Gui.TransitiveObject()
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    @ConfigEntry.Category("scrolling")
    @ConfigEntry.Gui.TransitiveObject()
    public Scrolling scrolling = new Scrolling();

    @ConfigEntry.Gui.PrefixText()
    public boolean settingsButton = true;
    public boolean enableBook = true;

    @Override
    public void validatePostLoad() {
        syncRbipConfig();
    }

    /**
     * Writes RBIP extended features and bottom number to RBIP's JSON config file.
     * RBIP's reloadIfChanged() picks up the change on its next check (hot reload).
     */
    public void syncRbipConfig() {
        try {
            JsonObject object = new JsonObject();
            object.addProperty("extended_features", this.rbip.enableRecipeBookIsPain);
            object.addProperty("bottom_number", this.rbip.enableTabPage ? 16 : 6);

            Path configDir = Path.of("config");
            Files.createDirectories(configDir);
            Path configFile = configDir.resolve("recipe-book-is-pain-extended.json");
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(object, writer);
            }
        } catch (IOException e) {
            com.alonie.brbe.BetterRecipeBook.LOGGER.warn("[BRBE] Could not sync RBIP config", e);
        }
    }

    public static class RecipeBookIsPain {
        @ConfigEntry.Gui.Tooltip()
        public boolean enableRecipeBookIsPain = true;

        public boolean enableTabPage = true;
    }
}
