package com.alonie.brbe.mixins.localcache;

import com.alonie.brbe.cache.VanillaRecipeCache;
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
 * Hooks into ClientRecipeBook.rebuildCollections() to inject locally-cached
 * vanilla recipe entries before the rebuild on recipe-sparse servers.
 *
 * Recipes are loaded at mod init from the Minecraft JAR's built-in recipe JSONs.
 * No capture step or file I/O needed.
 */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {

    @Shadow
    private Map<RecipeDisplayId, RecipeDisplayEntry> known;

    /**
     * Before vanilla rebuilds collections, check if the server is recipe-sparse.
     * If so, inject cached entries into the known map so they appear in the UI.
     */
    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void brbe$preRebuildInjectCache(CallbackInfo ci) {
        if (!VanillaRecipeCache.hasEntries()) return;
        VanillaRecipeCache.detectAndInject((ClientRecipeBook) (Object) this, known);
    }

    @Inject(method = "rebuildCollections", at = @At("RETURN"))
    private void brbe$postRebuildLog(CallbackInfo ci) {
        com.alonie.brbe.BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] rebuild RETURN — known={}", known.size());
    }
}
