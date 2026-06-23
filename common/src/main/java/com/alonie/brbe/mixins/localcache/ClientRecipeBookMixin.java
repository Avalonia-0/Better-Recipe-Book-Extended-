package com.alonie.brbe.mixins.localcache;

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
 * Hooks into ClientRecipeBook.rebuildCollections() to inject locally-cached
 * vanilla recipe entries before the rebuild on recipe-sparse servers.
 *
 * Recipes are loaded at mod init from the Minecraft JAR's built-in recipe JSONs.
 * No capture step or file I/O needed.
 *
 * Delegates to {@link RecipeBookState} for lifecycle coordination —
 * cache injection happens inside {@code beginCycle()}, and state cleanup
 * happens inside {@code endCycle()}.
 */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {

    @Shadow
    private Map<RecipeDisplayId, RecipeDisplayEntry> known;

    /**
     * Begin the rebuildCollections lifecycle.
     * RecipeBookState.beginCycle() handles cache injection internally.
     */
    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void brbe$preRebuildInjectCache(CallbackInfo ci) {
        RecipeBookState.beginCycle((ClientRecipeBook) (Object) this, known);
    }

    /** End the rebuildCollections lifecycle. */
    @Inject(method = "rebuildCollections", at = @At("RETURN"))
    private void brbe$postRebuildEndCycle(CallbackInfo ci) {
        com.alonie.brbe.BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] rebuild RETURN — known={}", known.size());
        RecipeBookState.endCycle();
    }
}
