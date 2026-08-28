package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.interfaces.unlockrecipes.IMixinRecipeManager;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

public class RecipeUnlockUtil {

    private static final Logger LOG = LogManager.getLogger("brbe-diag");

    /** 上次生效的 unlockAll 状态，用于检测配置切换。 */
    private static Boolean lastUnlockAll;

    public static void unlockRecipesIfRequired() {
        boolean unlockAll = BetterRecipeBook.ctx().config().newRecipes.unlockAll;
        if (unlockAll) {
            unlockRecipes();
        }
        lastUnlockAll = unlockAll;
    }

    /**
     * Called on config change.  Syncs the local recipe book's unlocked
     * state to the unlockAll toggle: turning it on unlocks everything,
     * turning it off revokes the locally-unlocked recipes (restoring the
     * server-authoritative unlock set).
     */
    public static void syncToConfig() {
        boolean unlockAll = BetterRecipeBook.ctx().config().newRecipes.unlockAll;
        if (lastUnlockAll != null && lastUnlockAll == unlockAll) return;
        if (unlockAll) {
            unlockRecipes();
        } else {
            revokeUnlockAll();
        }
        lastUnlockAll = unlockAll;
    }

    /**
     * Removes the locally-unlocked recipes (added by {@link #unlockRecipes()})
     * that the server has not actually unlocked, restoring vanilla unlock
     * behavior.  Pure client-side — the server's unlock state is untouched.
     */
    public static void revokeUnlockAll() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.connection == null || minecraft.level == null) {
            return;
        }

        RecipeManager recipeManager = player.connection.getRecipeManager();
        ClientRecipeBook recipeBook = player.getRecipeBook();
        Set<ResourceLocation> serverUnlocked =
                ((IMixinRecipeManager) recipeManager).brbe$getServerUnlockedRecipes();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (!serverUnlocked.contains(holder.id())) {
                recipeBook.remove(holder);
            }
        }

        // Rebuild collections + recompute known so vanilla's
        // removeIf(!hasKnownRecipes) correctly hides the revoked ones.
        ((ClientRecipeBookAccessor) recipeBook)
                .brbe$setupCollections(recipeManager.getRecipes(), minecraft.level.registryAccess());
        recipeBook.getCollections().forEach(c -> c.updateKnownRecipes(recipeBook));
        if (minecraft.screen instanceof RecipeUpdateListener rul) {
            rul.recipesUpdated();
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
