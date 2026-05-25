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

    private static IntegratedServer betterRecipeBook$server;
    private static java.util.UUID betterRecipeBook$playerId;
    private static Set<ResourceKey<Recipe<?>>> betterRecipeBook$originalRecipes = Set.of();
    private static Set<RecipeDisplayId> betterRecipeBook$originalRecipeDisplays = Set.of();
    private static boolean betterRecipeBook$hasSnapshot;

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
        if (!betterRecipeBook$hasSnapshot || betterRecipeBook$server == null || betterRecipeBook$playerId == null) {
            betterRecipeBook$clearSession();
            return;
        }

        IntegratedServer server = betterRecipeBook$server;
        java.util.UUID playerId = betterRecipeBook$playerId;
        Set<ResourceKey<Recipe<?>>> originalRecipes = Set.copyOf(betterRecipeBook$originalRecipes);

        server.submit(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                return;
            }

            ServerRecipeBook recipeBook = serverPlayer.getRecipeBook();
            Set<ResourceKey<Recipe<?>>> recipesToRemove = new HashSet<>(((ServerRecipeBookAccessor) recipeBook).betterRecipeBook$getKnown());
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

        betterRecipeBook$clearSession();
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
        Set<RecipeDisplayId> clientKnownDisplays = Set.copyOf(((ClientRecipeBookAccessor) minecraft.player.getRecipeBook()).betterRecipeBook$getKnown().keySet());
        server.submit(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
            if (serverPlayer == null) {
                return;
            }

            if (betterRecipeBook$hasSnapshot && betterRecipeBook$server == server && playerId.equals(betterRecipeBook$playerId)) {
                return;
            }

            if (!betterRecipeBook$hasSnapshot || betterRecipeBook$server != server || !playerId.equals(betterRecipeBook$playerId)) {
                betterRecipeBook$server = server;
                betterRecipeBook$playerId = playerId;
                betterRecipeBook$originalRecipes = Set.copyOf(((ServerRecipeBookAccessor) serverPlayer.getRecipeBook()).betterRecipeBook$getKnown());
                betterRecipeBook$originalRecipeDisplays = clientKnownDisplays;
                betterRecipeBook$hasSnapshot = true;
            }

            serverPlayer.awardRecipes(server.getRecipeManager().getRecipes());
        }).join();
    }

    public static boolean isTemporarilyUnlocked(RecipeDisplayId recipeDisplayId) {
        return BetterRecipeBook.config.newRecipes.unlockAll
                && betterRecipeBook$hasSnapshot
                && !betterRecipeBook$originalRecipeDisplays.contains(recipeDisplayId);
    }

    private static void betterRecipeBook$clearSession() {
        betterRecipeBook$server = null;
        betterRecipeBook$playerId = null;
        betterRecipeBook$originalRecipes = Set.of();
        betterRecipeBook$originalRecipeDisplays = Set.of();
        betterRecipeBook$hasSnapshot = false;
    }
}
