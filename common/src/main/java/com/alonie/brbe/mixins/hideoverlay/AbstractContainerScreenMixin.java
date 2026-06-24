package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts A/R/U keys on container screens when the "Hide REI/JEI Overlay" config is enabled.
 * <p>
 * When REI/JEI overlays are hidden:
 * - A key: consumed to prevent REI/JEI favorites (skipped if a text field is focused)
 * - R/U keys: routed to {@link ItemViewCompat} for recipe/usage lookup
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // GLFW key constants
    private static final int KEY_A = 65;
    private static final int KEY_R = 82;
    private static final int KEY_U = 85;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$handleKeysOnHiddenOverlay(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!BetterRecipeBook.config.hideReiJeiOverlay) {
            return;
        }

        int keyCode = event.key();

        // A key: prevent REI/JEI favorites from processing.
        // If a text field (search box) is focused, let the key through for typing.
        if (keyCode == KEY_A) {
            Screen screen = (Screen) (Object) this;
            if (!(screen.getFocused() instanceof EditBox)) {
                cir.setReturnValue(true);
            }
            return;
        }

        if (keyCode != KEY_R && keyCode != KEY_U) {
            return;
        }

        if (!ItemViewCompat.isLoaded()) {
            return;
        }

        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack stack = slot.getItem();
        boolean handled = keyCode == KEY_R
                ? ItemViewCompat.openRecipeView(stack)
                : ItemViewCompat.openUsageView(stack);

        if (handled) {
            cir.setReturnValue(true);
        }
    }
}
