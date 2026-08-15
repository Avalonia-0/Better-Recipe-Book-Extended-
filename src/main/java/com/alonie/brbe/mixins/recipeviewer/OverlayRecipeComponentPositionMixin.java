package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.util.AlternativeOverlayLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Keeps the vanilla alternative-recipe overlay on screen for every entry path:
 * the vanilla recipe-book button press (which lays out with a non-zero delta and
 * can hug the screen edge) and the BRBE R/U viewer alike.  After {@code init}
 * builds the buttons, recompute the actual box size and clamp x/y so the box
 * stays >= 30px from every screen edge when it fits, or is pulled fully inside
 * the screen otherwise — the same rule the R/U viewer applies.
 */
@Mixin(OverlayRecipeComponent.class)
public abstract class OverlayRecipeComponentPositionMixin {

    @Shadow
    private int x;

    @Shadow
    private int y;

    @Shadow
    @Final
    private List<?> recipeButtons;

    @Inject(method = "init", at = @At("RETURN"))
    private void brbe$keepOverlayOnScreen(RecipeCollection collection, ContextMap contextMap,
                                          boolean isFiltering, int initX, int initY,
                                          int initW, int initH, float delta, CallbackInfo ci) {
        int count = this.recipeButtons.size();
        if (count == 0) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int columns = AlternativeOverlayLayout.columnsFor(count);
        int rows = (count + columns - 1) / columns;
        int boxW = Math.min(count, columns) * 25 + 8;
        int boxH = rows * 25 + 8;

        int boxX;
        if (boxW <= screenW - 60) {
            boxX = Math.max(30, Math.min(this.x, screenW - boxW - 30));
        } else {
            boxX = Math.max(0, Math.min(this.x, screenW - boxW));
        }
        int boxY;
        if (boxH <= screenH - 60) {
            boxY = Math.max(30, Math.min(this.y, screenH - boxH - 30));
        } else {
            boxY = Math.max(0, Math.min(this.y, screenH - boxH));
        }
        this.x = boxX;
        this.y = boxY;
    }
}
