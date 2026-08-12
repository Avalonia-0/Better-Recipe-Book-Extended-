package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.ServerRecipeBookAccessor;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives the "unlock all recipes" toggle purely client-side.
 *
 * <p>The server recipe book is never modified here: unlock-all only adds
 * entries to the local {@link ClientRecipeBook} known map, and revoking it
 * removes everything the server did not send us (tracked in
 * {@link #getServerUnlockedRecipes()}).  Because the player's persisted server
 * recipe book stays untouched, turning the toggle off keeps working across a
 * restart — unlike the previous implementation, which awarded every recipe
 * server-side and then relied on an in-memory snapshot to revoke them.</p>
 */
public class RecipeUnlockUtil {

    private static final Logger LOG = LogManager.getLogger("brbe-unlock");

    /**
     * Server-authoritative unlocked displays, maintained by
     * {@code ClientPacketListenerMixin} from the recipe-book add/remove
     * packets.  This is the base line {@link #revokeUnlockAll()} restores to.
     */
    private static final Set<RecipeDisplayId> serverUnlockedRecipes = new HashSet<>();

    /** Last effective unlockAll value — used to detect config toggles. */
    private static Boolean lastUnlockAll;

    private RecipeUnlockUtil() {}

    public static Set<RecipeDisplayId> getServerUnlockedRecipes() {
        return serverUnlockedRecipes;
    }

    /**
     * Called on every recipe-book packet.  Re-applies unlock-all and, when the
     * toggle is off, repairs a server book polluted by the old implementation.
     */
    public static void unlockRecipesIfRequired() {
        boolean unlockAll = BetterRecipeBook.config.newRecipes.unlockAll;
        if (unlockAll) {
            unlockRecipes();
        } else {
            repairPollutedServerBook();
        }
        lastUnlockAll = unlockAll;
    }

    /** Called when the config changes.  Applies the unlockAll toggle. */
    public static void syncToConfig() {
        boolean unlockAll = BetterRecipeBook.config.newRecipes.unlockAll;
        if (lastUnlockAll != null && lastUnlockAll == unlockAll) {
            return;
        }
        if (unlockAll) {
            unlockRecipes();
        } else {
            revokeUnlockAll();
            repairPollutedServerBook();
        }
        lastUnlockAll = unlockAll;
    }

    /**
     * Adds every recipe display the singleplayer server knows of to the local
     * recipe book.  Pure client-side: the server book is untouched.
     */
    public static void unlockRecipes() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.hasSingleplayerServer()) {
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        List<RecipeDisplayEntry> allDisplays = new ArrayList<>();
        try {
            server.submit(() -> {
                RecipeManager recipeManager = server.getRecipeManager();
                for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                    recipeManager.listDisplaysForRecipe(holder.id(), allDisplays::add);
                }
            }).join();
        } catch (Exception e) {
            LOG.warn("[BRBE] unlock-all failed to enumerate recipes", e);
            return;
        }

        ClientRecipeBook book = minecraft.player.getRecipeBook();
        for (RecipeDisplayEntry entry : allDisplays) {
            book.add(entry);
        }
        book.rebuildCollections();
    }

    /**
     * Removes every locally-added display the server did not send — both the
     * unlock-all additions and the cache-injected vanilla entries (negative
     * index), so the book returns to the server-authoritative state.
     */
    public static void revokeUnlockAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientRecipeBook book = minecraft.player.getRecipeBook();
        ClientRecipeBookAccessor accessor = (ClientRecipeBookAccessor) book;
        List<RecipeDisplayId> toRemove = new ArrayList<>();
        for (RecipeDisplayId id : accessor.brbe$getKnown().keySet()) {
            if (!serverUnlockedRecipes.contains(id)) {
                toRemove.add(id);
            }
        }
        if (toRemove.isEmpty()) {
            return;
        }
        for (RecipeDisplayId id : toRemove) {
            book.remove(id);
        }
        book.rebuildCollections();
    }

    /**
     * Called when leaving a world — undoes the local unlock-all so the next
     * world starts clean (the server re-sends its own state anyway).
     */
    public static void restoreRecipes() {
        revokeUnlockAll();
    }

    /**
     * One-time repair for worlds polluted by the pre-fix implementation, which
     * awarded every recipe into the server recipe book.  That state is now
     * persisted in the player data, so the server keeps re-sending everything
     * and the unlock-all toggle cannot be turned off.  When detected (toggle
     * off, server book holds nearly every recipe — vanilla never produces this
     * state) the server book is reset so the client sees real unlock progress
     * again.
     */
    private static void repairPollutedServerBook() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.hasSingleplayerServer()) {
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }

        java.util.UUID playerId = minecraft.player.getUUID();
        try {
            server.submit(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
                if (serverPlayer == null) {
                    return;
                }
                ServerRecipeBook book = serverPlayer.getRecipeBook();
                Set<ResourceKey<Recipe<?>>> known = ((ServerRecipeBookAccessor) book).brbe$getKnown();
                int total = server.getRecipeManager().getRecipes().size();
                if (total == 0 || known.size() < total * 0.9) {
                    return;
                }
                List<RecipeHolder<?>> all = new ArrayList<>();
                for (ResourceKey<Recipe<?>> key : known) {
                    server.getRecipeManager().byKey(key).ifPresent(all::add);
                }
                book.removeRecipes(all, serverPlayer);
                LOG.warn("[BRBE] reset unlock-all-polluted server recipe book: removed {} known recipes",
                        all.size());
            }).join();
        } catch (Exception e) {
            LOG.warn("[BRBE] failed to repair polluted server recipe book", e);
        }
    }
}
