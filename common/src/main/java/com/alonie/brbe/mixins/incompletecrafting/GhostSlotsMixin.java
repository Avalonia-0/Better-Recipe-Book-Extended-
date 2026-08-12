package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.util.PartialGhostOverlayUtil;
import net.minecraft.client.gui.GuiGraphics;
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
 * {@code GhostSlots} 绘制红色背景与白色半透明罩时查询该状态并跳过这些槽位。
 *
 * <p>1.21.11 的 {@code GhostSlots} 渲染：{@code render} 通过 forEach 委托到私有
 * 方法 {@code method_62030}（Yarn 私有名），其中有 3 处 {@code fill(IIIII)V}：
 * 结果槽大格子红底 {@code (x-4,y-4,x+20,y+20)}、普通材料槽红底
 * {@code (x,y,x+16,y+16)}、白色半透明罩 {@code (x,y,x+16,y+16)}。单个
 * {@code @Redirect} 统一拦截，按颜色区分。
 */
@Mixin(GhostSlots.class)
public abstract class GhostSlotsMixin {

    @Redirect(
            method = "method_62030",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void brbe$skipRedMaskForAvailableMaterials(GuiGraphics graphics, int x0, int y0, int x1, int y1, int col) {
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
