package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that cancels {@code IconButton.draw()} for JEI's bottom overlay toolbar
 * buttons when BRBE's "Hide REI/JEI" config is enabled.
 * <p>
 * Only buttons belonging to JEI's overlay toolbar (config, bookmark, history)
 * are cancelled — these are the three small buttons at the bottom of the screen
 * on container GUIs. JEI's recipe/usage viewer also has {@code IconButton}s
 * (navigation arrows, "show craftable" toggle), but those are rendered inside
 * JEI's own {@code RecipesGui} screen and are left untouched.
 * <p>
 * Discrimination is done by checking whether the current active screen is
 * JEI's internal {@code RecipesGui}. If it is, the draw proceeds normally;
 * otherwise it is cancelled. This is inherently correct because the overlay
 * toolbar buttons are only active during container screens, never inside
 * JEI's own GUIs.
 * <p>
 * Uses {@code targets} (string) instead of {@code value} (class literal) so that
 * the Mixin annotation processor does not require the JEI jar at compile time.
 * {@code remap = false} ensures no refmap remapping is attempted.
 */
@Mixin(targets = "mezz.jei.gui.elements.IconButton", remap = false)
public abstract class IconButtonMixin {

    /** Lazily-resolved reference to JEI's RecipesGui class (avoids repeated Class.forName). */
    private static Class<?> recipesGuiClass;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelOverlayIconButtonDraw(CallbackInfo ci) {
        if (BetterRecipeBook.config == null || !BetterRecipeBook.config.hideReiJeiOverlay) {
            return;
        }
        // Only cancel on non-JEI screens (overlay is visible on container screens).
        // Inside JEI's own RecipesGui, its own IconButtons must work normally.
        if (isJeiScreen()) {
            return;
        }
        ci.cancel();
    }

    /**
     * Returns {@code true} if the current active screen is a JEI internal screen
     * (such as {@code RecipesGui}), in which case overlay buttons are not active
     * and {@code IconButton.draw()} calls belong to JEI's own UI.
     */
    private static boolean isJeiScreen() {
        try {
            Screen current = Minecraft.getInstance().screen;
            if (current == null) return false;
            if (recipesGuiClass == null) {
                recipesGuiClass = Class.forName("mezz.jei.gui.recipes.RecipesGui");
            }
            return recipesGuiClass.isInstance(current);
        } catch (Exception e) {
            // JEI not loaded or class not found — assume not a JEI screen
            return false;
        }
    }
}
