package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.util.PartialGhostOverlayUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 残缺配方幽灵物品的遮罩：对物品栏已拥有的材料槽位跳过红色背景填充，
 * 同时跳过白色半透明罩，让物品完整不透明显示（提升对比度）。
 *
 * <p>每次渲染幽灵物品前，{@code PartialGhostOverlayUtil#prepare} 会按槽位顺序
 * （从左到右、从上到下）扣减物品栏数量，标记出"已有材料"的槽位；本 Mixin 在
 * {@code GhostRecipe.render} 绘制红色背景与白色半透明罩时查询该状态并跳过这些
 * 槽位。{@code render} 内有两处 0x30FF0000 红色填充（结果槽大格子与普通材料槽，
 * 均为 5 参 {@code fill}）与一处 0x30FFFFFF 白色半透明罩（6 参 {@code fill}，
 * 带 RenderType），分别由两个 {@code @Redirect} 拦截。
 */
@Mixin(GhostRecipe.class)
public abstract class GhostRecipeMixin {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void brbe$skipRedMaskForAvailableMaterials(GuiGraphics graphics, int x0, int y0, int x1, int y1, int col) {
        if (col == 0x30FF0000 && !PartialGhostOverlayUtil.shouldShowRedMask(x0, y0)) {
            return;
        }
        graphics.fill(x0, y0, x1, y1, col);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V")
    )
    private void brbe$skipWhiteOverlayForAvailableMaterials(GuiGraphics graphics, RenderType renderType, int x0, int y0, int x1, int y1, int col) {
        if (col == 0x30FFFFFF && !PartialGhostOverlayUtil.shouldShowRedMask(x0, y0)) {
            return;
        }
        graphics.fill(renderType, x0, y0, x1, y1, col);
    }
}
