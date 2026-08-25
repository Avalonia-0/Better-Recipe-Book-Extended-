package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.util.RecipeUnlockUtil;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

/**
 * Tracks the server-authoritative unlocked recipe displays and re-applies the
 * unlock-all toggle whenever the server updates the recipe book.
 *
 * <p>Also throttles {@link ClientPacketListener#refreshRecipeBook}: the server
 * sends one recipe-book packet per unlocked recipe (picking up a stick can
 * produce dozens of packets in one second), and vanilla refreshRecipeBook runs
 * a FULL rebuild on EVERY packet — {@code rebuildCollections} over the whole
 * known set, the session search-tree rebuild, and (via the rebuild hook) the
 * RBIP creative-group rebuild.  With unlock-all the known set is already
 * complete, so the packet entries are no-op {@code put} overwrites: the known
 * content does not change, yet every packet pays the full rebuild cost.  A
 * cheap order-independent fingerprint of the known set cancels the whole
 * refresh chain while the content is unchanged.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Unique
    private static int brbe$lastRefreshFingerprint = Integer.MIN_VALUE;

    @Unique
    private static boolean brbe$forceNextRefresh;

    @Inject(method = "handleRecipeBookAdd", at = @At("RETURN"))
    private void onRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {
        Set<RecipeDisplayId> serverUnlocked = RecipeUnlockUtil.getServerUnlockedRecipes();
        if (packet.replace()) {
            // The server replaced the whole book: vanilla clears the known map
            // (wiping any unlock-all injection).  Reset the applied flag so
            // unlock-all re-injects after the replacement.
            serverUnlocked.clear();
            RecipeUnlockUtil.onServerBookReplaced();
        }
        for (ClientboundRecipeBookAddPacket.Entry entry : packet.entries()) {
            RecipeDisplayId id = entry.contents().id();
            serverUnlocked.add(id);
            // Mark-transfer: this recipe was unlocked by progression, so it is
            // now a vanilla (server-unlocked) recipe, no longer a BRBE-imported
            // one.  Drop it from the unlock-all marker so isBrbeImported()
            // answers false for it (it stays in the book either way; the
            // marker only drives the BRBE-imported classification).
            RecipeUnlockUtil.markServerUnlocked(id);
        }
        RecipeUnlockUtil.unlockRecipesIfRequired();
    }

    @Inject(method = "handleRecipeBookRemove", at = @At("RETURN"))
    private void onRecipeBookRemove(ClientboundRecipeBookRemovePacket packet, CallbackInfo ci) {
        Set<RecipeDisplayId> serverUnlocked = RecipeUnlockUtil.getServerUnlockedRecipes();
        for (RecipeDisplayId id : packet.recipes()) {
            serverUnlocked.remove(id);
        }
        RecipeUnlockUtil.unlockRecipesIfRequired();
    }

    /**
     * Throttle refreshRecipeBook while the known set is unchanged.  Called
     * from handleRecipeBookAdd/Remove/Settings once per packet; the known set
     * is the single source of truth for every rebuild it triggers, so when
     * its content is identical to the last refresh the whole chain
     * (rebuildCollections + search trees + recipesUpdated) is a no-op.
     */
    @Inject(method = "refreshRecipeBook", at = @At("HEAD"), cancellable = true)
    private void brbe$skipUnchangedRefresh(ClientRecipeBook book, CallbackInfo ci) {
        if (brbe$forceNextRefresh) {
            // Settings packets do not change the known set but must still
            // reach the UI (filtering state sync).  Let this one through and
            // re-arm throttling.
            brbe$forceNextRefresh = false;
            brbe$lastRefreshFingerprint = brbe$knownFingerprint(book);
            return;
        }
        int fp = brbe$knownFingerprint(book);
        if (fp == brbe$lastRefreshFingerprint) {
            ci.cancel();
        } else {
            brbe$lastRefreshFingerprint = fp;
        }
    }

    @Inject(method = "handleRecipeBookSettings", at = @At("RETURN"))
    private void brbe$forceRefreshOnSettings(ClientboundRecipeBookSettingsPacket packet, CallbackInfo ci) {
        brbe$forceNextRefresh = true;
    }

    /** Order-independent fingerprint of the known set (ids summed), so
     *  unchanged content is detected in O(n) regardless of map iteration
     *  order (negative-index cache entries are deleted and rebuilt on each
     *  injection pass, which churns HashMap iteration order). */
    @Unique
    private static int brbe$knownFingerprint(ClientRecipeBook book) {
        Map<RecipeDisplayId, RecipeDisplayEntry> known =
                ((ClientRecipeBookAccessor) book).brbe$getKnown();
        int hash = 0;
        for (RecipeDisplayId id : known.keySet()) {
            hash += id.index() * 31;
        }
        return hash;
    }
}
