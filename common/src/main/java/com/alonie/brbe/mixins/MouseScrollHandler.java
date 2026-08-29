package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseScrollHandler {
    @Final @Shadow
    private Minecraft minecraft;

    /**
     * 滚轮总入口（1.21.1 无 Screen.mouseScrolled 分发链——1.21.2+ 才有，
     * 滚轮只有 MouseHandler.onScroll 一条路）。
     * <p>查询浮层打开时滚轮先给 viewer（翻页/切标签/滑工作站列），消费则
     * cancel 吞掉原版滚轮处理（创造标签栏/旁观者速度/背包切换等）；否则
     * 走配方书 queuedScroll 老路径。</p>
     */
    @Inject(at = @At("HEAD"), method = "onScroll", cancellable = true)
    public void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (RecipeViewerOverlay.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.mouseHandler != null && mc.getWindow() != null) {
                // GUI 缩放换算（与 vanilla 同款）：viewer 命中判定在 GUI 坐标系
                double x = mc.mouseHandler.xpos();
                double y = mc.mouseHandler.ypos();
                int winW = Math.max(1, mc.getWindow().getScreenWidth());
                int winH = Math.max(1, mc.getWindow().getScreenHeight());
                x = x * mc.getWindow().getGuiScaledWidth() / winW;
                y = y * mc.getWindow().getGuiScaledHeight() / winH;
                if (RecipeViewerOverlay.mouseScrolled(x, y, vertical)) {
                    ci.cancel();
                    return;
                }
            }
        }
        if (vertical != 0 && BetterRecipeBook.getQueuedScroll() == 0) {
            BetterRecipeBook.setQueuedScroll(vertical > 0 ? -1 : 1);
        }
    }
}
