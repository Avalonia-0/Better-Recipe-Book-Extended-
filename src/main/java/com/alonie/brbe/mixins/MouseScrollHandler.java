package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.RecipeBookGesture;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配方书滚轮的接缝入口（BRBE 侧）。
 *
 * <p>HEAD 注入 {@code MouseHandler.onScroll}：先让 {@link RecipeBookGesture#claimScroll}
 * 判定归属，认领则 {@code ci.cancel()}——原版方法体（{@code Screen.mouseScrolled}、
 * 快捷栏滚动）与其它模组的滚轮实现都收不到这次事件。RBIP 的同名 HEAD 注入调用同一个判定，
 * 先跑到的那一个认领、另一个被 cancel 跳过，因此<b>注入顺序不影响结果</b>。</p>
 *
 * <p>兜底：不属于原版配方书界面时沿用旧的"无条件入队"行为——BRBE 自研的酿造台/锻造台书
 * 没有竞争者，由它们各自的页面按自己的命中区域消费
 * {@link BetterRecipeBook#queuedScroll}。</p>
 */
@Mixin(MouseHandler.class)
public class MouseScrollHandler {
    @Final @Shadow
    private Minecraft minecraft;

    @Shadow
    private double getScaledXPos(Window window) {
        throw new AssertionError();
    }

    @Shadow
    private double getScaledYPos(Window window) {
        throw new AssertionError();
    }

    @Inject(at = @At(value = "HEAD"), method = "onScroll", cancellable = true)
    public void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.getWindow() == null
                || window != this.minecraft.getWindow().handle()) {
            return;
        }
        Window win = this.minecraft.getWindow();
        if (RecipeBookGesture.claimScroll(this.getScaledXPos(win), this.getScaledYPos(win), vertical)) {
            ci.cancel();
            return;
        }
        if (BetterRecipeBook.queuedScroll == 0 && vertical != 0.0D) {
            double d = (this.minecraft.options.discreteMouseScroll().get()
                    ? Math.signum(vertical) : vertical)
                    * this.minecraft.options.mouseWheelSensitivity().get();
            BetterRecipeBook.queuedScroll = (int) -Math.signum(d);
        }
    }
}
