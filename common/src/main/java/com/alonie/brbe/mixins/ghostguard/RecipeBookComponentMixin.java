package com.alonie.brbe.mixins.ghostguard;

import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skip rendering an empty ghost recipe.
 *
 * <p>Vanilla's {@code GhostRecipe.render} iterates its ingredient list and
 * is safe when empty, but third-party bytecode transforms (notably Sinytra
 * Connector's pre-launch transform on NeoForge) can break that path so an
 * empty ghost recipe throws {@code IndexOutOfBoundsException} on every frame
 * the crafting screen is open.  Cancelling the render call for empty ghost
 * recipes keeps the screen usable regardless of how the class is transformed.
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {

    @Inject(method = "renderGhostRecipe", at = @At("HEAD"), cancellable = true)
    private void brbe$skipEmptyGhostRender(GuiGraphics gui, int x, int y, boolean bl, float delta, CallbackInfo ci) {
        GhostRecipe ghost = ((RecipeBookComponentAccessor) this).getGhostRecipe();
        if (ghost == null || ghost.size() == 0) {
            ci.cancel();
        }
    }
}
