package com.alonie.brbe.mixins.toasts;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public class SuppressUnlockSound<T extends Toast> {
    @Shadow
    @Final
    private T toast;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;playSound(Lnet/minecraft/client/sounds/SoundManager;)V"))
    public void playSound(Toast.Visibility instance, SoundManager arg) {
        if (BetterRecipeBook.config.newRecipes.unlockAll && this.toast instanceof RecipeToast) {
            // pass
            return;
        }

        instance.playSound(arg);
    }
}
