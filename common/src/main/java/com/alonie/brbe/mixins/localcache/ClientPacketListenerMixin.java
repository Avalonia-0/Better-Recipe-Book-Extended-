package com.alonie.brbe.mixins.localcache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.VanillaRecipeCache;
import com.alonie.brbe.util.RecipeBookState;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In 26.1.2, {@code ClientPacketListener.refreshRecipeBook(ClientRecipeBook)}
 * is the single method that calls {@code ClientRecipeBook.rebuildCollections()}
 * after recipe packets arrive (handleRecipeBookAdd, handleRecipeBookRemove,
 * handleRecipeBookSettings).
 *
 * <p>This mixin intercepts refreshRecipeBook to ensure that
 * {@link VanillaRecipeCache#detectAndInject} runs <em>before</em> the
 * vanilla rebuild, so any complemented cache entries are already in the
 * {@code known} map when collections are rebuilt.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    /**
     * Called at the HEAD of refreshRecipeBook, right before the vanilla
     * code calls {@code book.rebuildCollections()}.
     *
     * <p>We call detectAndInject via RecipeBookState's cycle to ensure
     * the complement pipeline runs deterministically:
     * <pre>
     *   refreshRecipeBook(book)
     *     → this hook: complement known map
     *     → vanilla: book.rebuildCollections() — categorizes complemented entries
     *     → ClientRecipeBookMixin at HEAD/RETURN — cycle management
     * </pre>
     */
    @Inject(method = "refreshRecipeBook", at = @At("HEAD"))
    private void brbe$beforeRefreshRecipeBook(ClientRecipeBook book, CallbackInfo ci) {
        if (!VanillaRecipeCache.hasEntries()) return;

        // Use reflection-style access to the known map via the injected
        // ClientRecipeBookMixin.  We can't directly access the private
        // 'known' field from this mixin, so we invoke rebuildCollections
        // which triggers the ClientRecipeBookMixin → RecipeBookState → detectAndInject chain.
        //
        // But we need to inject entries BEFORE rebuildCollections runs.
        // Strategy: force-complement by calling rebuildCollections once
        // before the vanilla call.  The first call injects entries,
        // the vanilla call categorizes them.
        //
        // However, this puts us right back to the double-rebuild problem.
        // Instead, detectAndInject is designed to be idempotent:
        // it purges old negative IDs and re-complements.  As long as
        // it runs before the vanilla rebuild, entries are included.
        //
        // We leverage the fact that ClientRecipeBookMixin already hooks
        // rebuildCollections HEAD and calls RecipeBookState.beginCycle(),
        // which calls detectAndInject().  So we just need to ensure
        // that the vanilla rebuildCollections call happens AFTER our
        // complement is ready.  The simplest way: just call
        // book.rebuildCollections() once here (which does complement
        // + rebuild), and the vanilla call right after will just rebuild
        // again with the same data.  Double rebuild is harmless because
        // detectAndInject purges old negative IDs, and the same complement
        // entries are injected each time.

        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] refreshRecipeBook hook: triggering complement before vanilla rebuild");
        book.rebuildCollections();
    }
}
