package com.alonie.brbe.mixins.toasts;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.RecipeUnlockUtil;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deferred recipe-unlock toasts: while unlock-all is on, every recipe is
 * already in the book (nothing is "newly unlocked"), so the per-recipe
 * {@link RecipeToast} is suppressed and the display is recorded instead.
 * When unlock-all is turned off, the accumulated toasts are shown at once —
 * the player sees exactly what progression unlocked while the toggle was on.
 */
@Mixin(RecipeToast.class)
public class RemoveToasts {
    @Inject(at = @At("HEAD"), method = "addOrUpdate", cancellable = true)
    private static void deferWhileUnlockAll(ToastManager toastManager,
                                            RecipeDisplay display, CallbackInfo ci) {
        if (BetterRecipeBook.config.newRecipes.unlockAll) {
            // Defer: record the display so turning unlock-all off can show it.
            RecipeUnlockUtil.deferUnlockToast(display);
            ci.cancel();
        }
    }
}
