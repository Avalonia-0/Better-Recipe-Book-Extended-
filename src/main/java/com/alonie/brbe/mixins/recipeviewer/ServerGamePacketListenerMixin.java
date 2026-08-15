package com.alonie.brbe.mixins.recipeviewer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Server-side ghost-preview fix for the BRBE R/U recipe-viewer.
 *
 * <p>The viewer shows every recipe in the client's recipe-book {@code known}
 * set, which includes vanilla recipes injected by {@code VanillaRecipeCache}
 * that the server never sent to the recipe book.  Vanilla
 * {@code ServerGamePacketListenerImpl#handlePlaceRecipe} rejects those with
 * {@code serverRecipeBook.contains(recipeId)} == false and never sends a ghost
 * packet, so clicking them shows no ghost preview.  The contains check is a
 * mild anti-abuse guard (the recipe book normally only surfaces known recipes);
 * relaxing it is safe — the subsequent {@code handlePlacement} still validates
 * the recipe is placeable in the menu.  We relax it for all recipe-book
 * placements so the viewer's recipes work.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    /** Relax the recipe-book membership check so viewer recipes can ghost-place. */
    @Redirect(method = "handlePlaceRecipe",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/resources/ResourceKey;)Z"))
    private boolean brbe$allowViewerGhost(ServerRecipeBook book, ResourceKey<?> recipeId) {
        return true;
    }
}
