package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.util.RecipeUnlockUtil;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Tracks the server-authoritative unlocked recipe displays and re-applies the
 * unlock-all toggle whenever the server updates the recipe book.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleRecipeBookAdd", at = @At("RETURN"))
    private void onRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {
        Set<RecipeDisplayId> serverUnlocked = RecipeUnlockUtil.getServerUnlockedRecipes();
        if (packet.replace()) {
            serverUnlocked.clear();
        }
        for (ClientboundRecipeBookAddPacket.Entry entry : packet.entries()) {
            serverUnlocked.add(entry.contents().id());
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
}
