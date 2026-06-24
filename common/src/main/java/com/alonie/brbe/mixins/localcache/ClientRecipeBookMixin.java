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
 * Hooks into ClientRecipeBook to inject locally-cached vanilla recipe
 * entries into the {@code known} map at multiple lifecycle points.
 *
 * <h3>Why multiple hooks</h3>
 * In 26.1.2, {@code rebuildCollections()} is only called from
 * {@code ClientPacketListener.refreshRecipeBook()}, which fires when
 * recipe packets arrive.  Servers like Hypixel do not send recipe
 * packets, so rebuildCollections is never called during gameplay.
 *
 * <p>To handle all server behaviors, we inject cache entries at three
 * points:
 * <ol>
 *   <li><b>Constructor RETURN</b> — initial injection during client
 *       init, before any server communication.</li>
 *   <li><b>clear() RETURN</b> — re-inject after servers that send
 *       recipe packets with {@code replace=true} wipe the known map.</li>
 *   <li><b>rebuildCollections() HEAD</b> — refresh injection when
 *       collections are rebuilt (handles servers that DO send
 *       non-replace recipe packets).</li>
 * </ol>
 */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {

    @Shadow
    private Map<RecipeDisplayId, RecipeDisplayEntry> known;

    // ---- Constructor hook: initial injection during client init ----

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$onConstruct(CallbackInfo ci) {
        if (VanillaRecipeCache.hasEntries()) {
            VanillaRecipeCache.detectAndInject((ClientRecipeBook) (Object) this, known);
        }
    }

    // ---- clear() hook: re-inject after server wipes known map ----

    @Inject(method = "clear", at = @At("RETURN"))
    private void brbe$onClear(CallbackInfo ci) {
        if (VanillaRecipeCache.hasEntries()) {
            VanillaRecipeCache.detectAndInject((ClientRecipeBook) (Object) this, known);
        }
    }

    // ---- rebuildCollections hooks: refresh during normal builds ----

    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void brbe$preRebuildInjectCache(CallbackInfo ci) {
        RecipeBookState.beginCycle((ClientRecipeBook) (Object) this, known);
    }

    @Inject(method = "rebuildCollections", at = @At("RETURN"))
    private void brbe$postRebuildEndCycle(CallbackInfo ci) {
        com.alonie.brbe.BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] rebuild RETURN — known={}", known.size());
        RecipeBookState.endCycle();
    }
}
