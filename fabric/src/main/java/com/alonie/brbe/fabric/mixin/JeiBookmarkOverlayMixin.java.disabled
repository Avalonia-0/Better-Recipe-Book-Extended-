package com.alonie.brbe.fabric.mixin;

import com.alonie.brbe.BetterRecipeBook;
import mezz.jei.gui.elements.IconButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin into JEI's BookmarkOverlay to hide the bookmark and history buttons
 * when BRBE's "Hide REI/JEI Interface" config is enabled.
 * <p>
 * Both buttons are drawn unconditionally in drawScreen() regardless of isOverlayEnabled().
 */
@Mixin(targets = "mezz.jei.gui.overlay.bookmarks.BookmarkOverlay")
public abstract class JeiBookmarkOverlayMixin {

    @Redirect(method = "drawScreen",
            at = @At(value = "INVOKE",
                    target = "Lmezz/jei/gui/elements/IconButton;draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void betterRecipeBook$hideButtons(IconButton button, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!BetterRecipeBook.config.hideReiJeiOverlay) {
            button.draw(gui, mouseX, mouseY, delta);
        }
    }
}
