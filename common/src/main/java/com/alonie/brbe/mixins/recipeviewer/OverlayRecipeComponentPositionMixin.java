package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.util.AlternativeOverlayLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 1.21.1 版替代配方浮层贴边钳位（1.21.11 OverlayRecipeComponentPositionMixin 移植）。
 *
 * <p>1.21.1 的 {@code OverlayRecipeComponent.init} 签名是 7 参
 * {@code (Minecraft, RecipeCollection, int, int, int, int, float)}（无 ContextMap/
 * isFiltering）。init 构建按钮后按 {@link AlternativeOverlayLayout#columnsFor}
 * 重算实际盒尺寸并钳位 x/y：能放下时距离屏幕边缘 >= 30px，否则完全拉进屏内。</p>
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
    private void brbe$keepOverlayOnScreen(Minecraft minecraft, RecipeCollection collection,
                                          int initX, int initY, int initW, int initH,
                                          float delta, CallbackInfo ci) {
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
