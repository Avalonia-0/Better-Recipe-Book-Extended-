package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.util.PartialGhostOverlayUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 残缺配方幽灵物品的遮罩：对物品栏已拥有的材料槽位跳过红色背景填充，
 * 同时跳过白色半透明罩，让物品完整不透明显示（提升对比度）。
 *
 * <p>每次渲染幽灵物品前，{@code PartialGhostOverlayUtil#prepare} 会按槽位顺序
 * （从左到右、从上到下）扣减物品栏数量，标记出"已有材料"的槽位；本 Mixin 在
 * {@code GhostSlots.extractRenderState} 绘制红色背景与白色半透明罩时查询该状态
 * 并跳过这些槽位。
 */
@Mixin(GhostSlots.class)
public abstract class GhostSlotsMixin {

    @Redirect(
            // GhostSlots.extractRenderState 通过 forEach 的 lambda 绘制每个槽位，
            // fill 调用实际位于编译器生成的 lambda$extractRenderState$0 内。
            method = "lambda$extractRenderState$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void brbe$skipRedMaskForAvailableMaterials(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int col) {
        if (!PartialGhostOverlayUtil.shouldShowRedMask(x0, y0)) {
            // 材料已满足的槽位：跳过红色背景（0x30FF0000）与白色半透明罩（0x30FFFFFF），
            // 物品图标以完整不透明度显示。
            if (col == 0x30FF0000 || col == 0x30FFFFFF) {
                return;
            }
        }
        graphics.fill(x0, y0, x1, y1, col);
    }
}
