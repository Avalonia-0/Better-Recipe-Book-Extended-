package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.cache.VanillaRecipeCache;
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
import net.minecraft.world.item.crafting.display.RecipeDisplay;
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
 * <p>Design: mark-then-filter.  Unlock-all does NOT mutate the server book;
 * it records every display it injected in {@link #unlockAllInjected} and adds
 * the display to the local {@link ClientRecipeBook}.  Turning the toggle off
 * removes exactly those marked displays ({@link #revokeUnlockAll}) — never the
 * server-synced recipes, which are identified by non-negative display index
 * (the server only sends non-negative indices; BRBE's vanilla-cache injections
 * are negative, see {@link VanillaRecipeCache#isLocalRecipe}).  Because the
 * marker set and the known map are both in memory, toggling applies
 * immediately (rebuildCollections → engine rebuild), no world reload needed.
 *
 * <p>{@link #isBrbeImported} is the same mark-then-filter pattern as the
 * workstation {@code recipeBook} flag: it distinguishes the BRBE-imported half
 * of the known set (unlock-all injections plus negative-index cache entries)
 * from the server-unlocked half, so a config toggle can hide the imported
 * recipes up front.
 */
public class RecipeUnlockUtil {

    private static final Logger LOG = LogManager.getLogger("zzzbrbe-unlock");

    /** Displays injected by unlock-all (server display ids — non-negative,
     *  but tracked here so revoke removes exactly these and no others). */
    private static final Set<RecipeDisplayId> unlockAllInjected = new HashSet<>();

    /**
     * Server-authoritative unlocked displays, maintained by
     * {@code ClientPacketListenerMixin} from the recipe-book add/remove
     * packets.  Kept for the pollution-repair check.
     */
    private static final Set<RecipeDisplayId> serverUnlockedRecipes = new HashSet<>();

    /** Recipe-unlock toasts deferred while unlock-all is on; flushed (shown
     *  all at once) when the toggle turns off. */
    private static final List<RecipeDisplay> deferredUnlockToasts = new ArrayList<>();

    /** Last effective unlockAll value — used to detect config toggles. */
    private static Boolean lastUnlockAll;

    /** Whether the full unlock-all injection is currently applied (set on
     *  successful unlockRecipes, cleared on revoke).  Guards the per-packet
     *  path so progression unlocks do not re-run the full enumeration. */
    private static boolean unlockAllApplied;

    private RecipeUnlockUtil() {}

    /** Record a recipe-unlock toast while unlock-all is on (its toast is
     *  suppressed by {@code RemoveToasts}); shown when unlock-all turns off. */
    public static void deferUnlockToast(RecipeDisplay display) {
        if (display != null) {
            deferredUnlockToasts.add(display);
        }
    }

    /** Show every deferred unlock toast now (called when unlock-all turns off),
     *  so the player sees what progression unlocked while the toggle was on. */
    public static void flushDeferredUnlockToasts() {
        if (deferredUnlockToasts.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            deferredUnlockToasts.clear();
            return;
        }
        net.minecraft.client.gui.components.toasts.ToastManager toastManager =
                minecraft.gui.toastManager();
        for (RecipeDisplay display : deferredUnlockToasts) {
            try {
                net.minecraft.client.gui.components.toasts.RecipeToast.addOrUpdate(
                        toastManager, display);
            } catch (Exception | LinkageError e) {
                // one bad display must not break the flush
            }
        }
        deferredUnlockToasts.clear();
        LOG.info("[BRBE] unlock-all flushed deferred unlock toasts");
    }

    public static Set<RecipeDisplayId> getServerUnlockedRecipes() {
        return serverUnlockedRecipes;
    }

    /**
     * A recipe was unlocked by progression (server recipe-book add packet):
     * transfer its classification from BRBE-imported to vanilla.  If it was
     * part of the unlock-all marker set, drop it from there — it is now a
     * server-unlocked (original) recipe, so {@link #isBrbeImported} must
     * answer false for it even while unlock-all is on.
     */
    public static void markServerUnlocked(RecipeDisplayId id) {
        if (id == null) return;
        unlockAllInjected.remove(id);
    }

    /**
     * The server replaced the whole recipe book (replace packet): vanilla
     * cleared the known map, wiping any unlock-all injection.  Reset the
     * applied flag so the next packet re-injects the full set.
     */
    public static void onServerBookReplaced() {
        unlockAllApplied = false;
        unlockAllInjected.clear();
    }

    /**
     * Whether {@code id} was imported into the recipe book by BRBE (unlock-all
     * injection, or the vanilla cache's negative-index entries) rather than
     * unlocked by the server's recipe-book progression.  Used by the
     * {@code hideBrbeImported} toggle: the known set = server-unlocked ∪
     * BRBE-imported; this marks the BRBE-imported half so the filter can drop
     * it at the source ({@code RecipeViewerIndex.knownEntries}).
     */
    public static boolean isBrbeImported(RecipeDisplayId id) {
        if (id == null) return false;
        // Negative index = vanilla-cache local injection.
        if (id.index() < 0) return true;
        // Non-negative but unlock-all injected (server display ids).
        return unlockAllInjected.contains(id);
    }

    /**
     * Called on every recipe-book packet (each progression unlock).  Re-applies
     * unlock-all and, when the toggle is off, repairs a server book polluted
     * by the old implementation.
     *
     * <p>While unlock-all is active, a progression unlock (e.g. crafting
     * sticks) must NOT re-run the full injection: every recipe is already in
     * the book and {@code unlockAllInjected} is already populated.  Guard on
     * {@link #unlockAllApplied} so the per-packet cost is O(1) — the full
     * enumeration only happens on the first application (world join or toggle
     * on) and after a revoke.
     */
    public static void unlockRecipesIfRequired() {
        boolean unlockAll = BetterRecipeBook.config.newRecipes.unlockAll;
        if (unlockAll && unlockAllApplied) {
            // Already fully unlocked — a progression packet adds nothing.
            return;
        }
        LOG.info("[BRBE] unlock-all recipe-book packet: unlockAll={} serverUnlocked={} applied={}",
                unlockAll, serverUnlockedRecipes.size(), unlockAllApplied);
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
        LOG.info("[BRBE] unlock-all syncToConfig: unlockAll={} last={}",
                unlockAll, lastUnlockAll);
        if (lastUnlockAll != null && lastUnlockAll == unlockAll) {
            return;
        }
        if (unlockAll) {
            unlockRecipes();
        } else {
            revokeUnlockAll();
            repairPollutedServerBook();
            // Now that the book shows server-unlocked recipes again, surface
            // the unlock toasts that were deferred while unlock-all was on.
            flushDeferredUnlockToasts();
        }
        lastUnlockAll = unlockAll;
        // Force every open recipe-book UI to rebuild from the fresh known set:
        // rebuildCollections alone does not re-run the screen's updateCollections
        // (which is what actually re-reads getCollections()), so without this
        // the toggle change is only visible after the next world load.
        forceRecipeBookUIRebuild();
    }

    /** Ask every currently-open recipe-book screen to rebuild its collections
     *  immediately (hot-reload the unlockAll toggle without a world reload). */
    private static void forceRecipeBookUIRebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) return;
        net.minecraft.client.gui.screens.Screen screen = minecraft.gui.screen();
        if (screen instanceof net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener listener) {
            listener.recipesUpdated();
        }
    }

    /**
     * Adds every recipe display the singleplayer server knows of to the local
     * recipe book, recording each in {@link #unlockAllInjected}.  Pure
     * client-side: the server book is untouched.  The injection (and the
     * {@code rebuildCollections} below it, which rebuilds the engine) applies
     * immediately — no world reload needed.
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
        unlockAllInjected.clear();
        for (RecipeDisplayEntry entry : allDisplays) {
            unlockAllInjected.add(entry.id());
            book.add(entry);
        }
        book.rebuildCollections();
        unlockAllApplied = true;
        LOG.info("[BRBE] unlock-all: injected {} displays", unlockAllInjected.size());
    }

    /**
     * Removes the unlock-all injections — the vanilla-cache negative-index
     * entries, plus every unlock-all display the server did not already unlock
     * — so the book returns to the server-authoritative state.
     *
     * <p>{@link #unlockAllInjected} holds the FULL server recipe set (unlock
     * all enumerates every recipe the singleplayer server knows), which also
     * contains the recipes the server itself unlocked.  Those must stay:
     * only the difference (server set minus server-unlocked) is BRBE's doing
     * and gets removed.  Server-unlocked ids come from
     * {@link #serverUnlockedRecipes} (the recipe-book add packets); the
     * vanilla-cache negative-index entries are always local and removed too.
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
            if (VanillaRecipeCache.isLocalRecipe(id)) {
                // Negative-index cache injection — always BRBE's, remove.
                toRemove.add(id);
                continue;
            }
            if (unlockAllInjected.contains(id) && !serverUnlockedRecipes.contains(id)) {
                // Unlock-all added it and the server never unlocked it.
                toRemove.add(id);
            }
        }
        if (toRemove.isEmpty()) {
            unlockAllInjected.clear();
            unlockAllApplied = false;
            return;
        }
        for (RecipeDisplayId id : toRemove) {
            book.remove(id);
        }
        unlockAllInjected.clear();
        unlockAllApplied = false;
        book.rebuildCollections();
        LOG.info("[BRBE] unlock-all revoked: removed {} displays, {} server-unlocked kept",
                toRemove.size(), accessor.brbe$getKnown().size());
    }

    /**
     * Called when leaving a world — undoes the local unlock-all so the next
     * world starts clean (the server re-sends its own state anyway).
     */
    public static void restoreRecipes() {
        unlockAllApplied = false;
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
