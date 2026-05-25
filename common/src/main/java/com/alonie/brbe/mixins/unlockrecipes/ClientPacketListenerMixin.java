package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.util.RecipeUnlockUtil;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the singleplayer client and integrated server recipe book in sync with the unlock-all setting.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleRecipeBookAdd", at = @At("RETURN"))
    private void onRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {
        RecipeUnlockUtil.syncToConfig();
    }
}
