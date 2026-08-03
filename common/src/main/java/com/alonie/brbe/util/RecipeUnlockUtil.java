package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.crafting.RecipeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecipeUnlockUtil {

    private static final Logger LOG = LogManager.getLogger("brbe-diag");

    public static void unlockRecipesIfRequired() {
        if (BetterRecipeBook.ctx().config().newRecipes.unlockAll) {
            unlockRecipes();
        }
    }

    public static void syncToConfig() {
        if (BetterRecipeBook.ctx().config().newRecipes.unlockAll) {
            unlockRecipes();
        }
    }

    /**
     * Unlocks all recipes that the RecipeManager knows of, then updates any screen implementing RecipeUpdateListener
     */
    public static void unlockRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.connection == null || minecraft.level == null) {
            return;
        }

        RecipeManager recipeManager = player.connection.getRecipeManager();
        ClientRecipeBook recipeBook = player.getRecipeBook();

        // Add all recipes to knownKeys
        recipeManager.getRecipes().forEach(recipeBook::add);

        // Force a full rebuild of collections from the recipe manager.
        // Without this, setupCollections may have been called earlier when
        // the recipe manager was only partially populated (during INIT packet
        // processing before handleUpdateRecipes), producing incomplete
        // collections frozen in the allCollections field.
        ((ClientRecipeBookAccessor) recipeBook)
                .brbe$setupCollections(recipeManager.getRecipes(), minecraft.level.registryAccess());

        // Update each collection's known-recipe state
        int knownColls = 0;
        for (var coll : recipeBook.getCollections()) {
            coll.updateKnownRecipes(recipeBook);
            if (coll.hasKnownRecipes()) knownColls++;
        }
        LOG.warn("[BRBE-DIAG] unlockRecipes DONE: collections={} knownColls={}",
                recipeBook.getCollections().size(), knownColls);
        if (minecraft.screen instanceof RecipeUpdateListener rul) {
            rul.recipesUpdated();
        }
    }

}
