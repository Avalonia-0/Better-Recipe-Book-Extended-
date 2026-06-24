package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.ServerRecipeBookAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.HashSet;
import java.util.Set;

public class RecipeUnlockUtil {

    private static IntegratedServer brbe$server;
    private static java.util.UUID brbe$playerId;
    private static Set<ResourceKey<Recipe<?>>> brbe$originalRecipes = Set.of();
    private static Set<RecipeDisplayId> brbe$originalRecipeDisplays = Set.of();
    private static boolean brbe$hasSnapshot;

    /**
     * Applies the current config to the integrated server's recipe book when available.
     */
    public static void syncToConfig() {
        if (BetterRecipeBook.config.newRecipes.unlockAll) {
            unlockRecipes();
        } else {
            restoreRecipes();
        }
    }

    public static void restoreRecipes() {
        if (!brbe$hasSnapshot || brbe$server == null || brbe$playerId == null) {
            brbe$clearSession();
            return;
        }

        IntegratedServer server = brbe$server;
        java.util.UUID playerId = brbe$playerId;
        Set<ResourceKey<Recipe<?>>> originalRecipes = Set.copyOf(brbe$originalRecipes);

        server.submit(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                return;
            }

            ServerRecipeBook recipeBook = serverPlayer.getRecipeBook();
            Set<ResourceKey<Recipe<?>>> recipesToRemove = new HashSet<>(((ServerRecipeBookAccessor) recipeBook).brbe$getKnown());
            recipesToRemove.removeAll(originalRecipes);
            if (recipesToRemove.isEmpty()) {
                return;
            }

            java.util.List<RecipeHolder<?>> removedRecipes = recipesToRemove.stream()
                    .map(server.getRecipeManager()::byKey)
                    .flatMap(java.util.Optional::stream)
                    .toList();

            if (!removedRecipes.isEmpty()) {
                recipeBook.removeRecipes(removedRecipes, serverPlayer);
            }
        }).join();

        brbe$clearSession();
    }

    public static void unlockRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.hasSingleplayerServer()) {
            return;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        java.util.UUID playerId = minecraft.player.getUUID();
        Set<RecipeDisplayId> clientKnownDisplays = Set.copyOf(((ClientRecipeBookAccessor) minecraft.player.getRecipeBook()).brbe$getKnown().keySet());
        server.submit(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                return;
            }

            if (brbe$hasSnapshot && brbe$server == server && playerId.equals(brbe$playerId)) {
                return;
            }

            if (!brbe$hasSnapshot || brbe$server != server || !playerId.equals(brbe$playerId)) {
                brbe$server = server;
                brbe$playerId = playerId;
                brbe$originalRecipes = Set.copyOf(((ServerRecipeBookAccessor) serverPlayer.getRecipeBook()).brbe$getKnown());
                brbe$originalRecipeDisplays = clientKnownDisplays;
                brbe$hasSnapshot = true;
            }

            serverPlayer.awardRecipes(server.getRecipeManager().getRecipes());
        }).join();
    }

    public static boolean isTemporarilyUnlocked(RecipeDisplayId recipeDisplayId) {
        return BetterRecipeBook.config.newRecipes.unlockAll
                && brbe$hasSnapshot
                && !brbe$originalRecipeDisplays.contains(recipeDisplayId);
    }

    private static void brbe$clearSession() {
        brbe$server = null;
        brbe$playerId = null;
        brbe$originalRecipes = Set.of();
        brbe$originalRecipeDisplays = Set.of();
        brbe$hasSnapshot = false;
    }
}
