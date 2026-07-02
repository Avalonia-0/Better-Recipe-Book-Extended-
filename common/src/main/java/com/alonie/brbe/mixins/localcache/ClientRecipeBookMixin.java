package com.alonie.brbe.mixins.localcache;

import com.alonie.brbe.cache.VanillaRecipeCache;
import com.alonie.brbe.util.RecipeBookState;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Injects locally-cached vanilla recipe entries into ClientRecipeBook at
 * two strategic points — constructor and rebuildCollections — with different
 * strategies at each point.
 *
 * <h3>Constructor: inject all</h3>
 * During client init, no server recipe data exists yet.  We inject ALL
 * valid cached entries unconditionally.  This covers servers that never
 * send recipe packets (Hypixel).
 *
 * <h3>rebuildCollections: complement</h3>
 * When the server sends recipe packets (singleplayer, most servers),
 * rebuildCollections is called from refreshRecipeBook after server entries
 * have been added.  At this point we only inject cache entries whose
 * result items are NOT already covered by the server — filling in gaps
 * without duplicating what the server provides.
 *
 * <p>If the server sent nothing (known has zero server entries at
 * rebuild time), we fall back to injecting all valid entries.
 *
 * <h3>clear(): do nothing</h3>
 * The server clears the known map before sending new recipes.  We let
 * the subsequent rebuildCollections handle injection — injecting here
 * would be wasted work since the entries are immediately wiped.
 */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {

    @Shadow
    private Map<RecipeDisplayId, RecipeDisplayEntry> known;

    // ---- Constructor: inject ALL (handles no-packet servers) ----

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$onConstruct(CallbackInfo ci) {
        if (!VanillaRecipeCache.hasEntries()) return;
        ClientRecipeBook self = (ClientRecipeBook) (Object) this;
        VanillaRecipeCache.detectAndInject(self, known);
        // Force rebuild so collectionsByTab is populated and the UI sees entries
        self.rebuildCollections();
    }

    // ---- rebuildCollections HEAD: complement (handles packet servers) ----

    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void brbe$preRebuildInjectCache(CallbackInfo ci) {
        RecipeBookState.beginCycle((ClientRecipeBook) (Object) this, known);
    }

    // ---- rebuildCollections RETURN: log + cleanup ----

    @Inject(method = "rebuildCollections", at = @At("RETURN"))
    private void brbe$postRebuildEndCycle(CallbackInfo ci) {
        com.alonie.brbe.BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] rebuild RETURN — known={}", known.size());
        VanillaRecipeCache.dumpAllKnown(known);
        RecipeBookState.endCycle();
    }
}
