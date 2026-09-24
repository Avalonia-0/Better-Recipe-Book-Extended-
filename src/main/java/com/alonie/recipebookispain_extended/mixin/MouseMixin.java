package com.alonie.recipebookispain_extended.mixin;

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
 * 配方书滚轮的接缝入口（RBIP 侧）。
 *
 * <p>2026-09-25 起这里<b>只做转发</b>：判定与消费统一在
 * {@link RecipeBookGesture#claimScroll}（BRBE 的 {@code MouseScrollHandler} 也调用它）。
 * 两个 HEAD 注入里先跑到的那个认领并 {@code ci.cancel()}，另一个连同原版方法体一起被跳过，
 * 所以注入顺序不再影响结果——此前"标签栏翻页"和"配方页翻页"是两套各自 cancel 的实现，
 * 谁先跑取决于 mixin 应用顺序。</p>
 *
 * <p>保留本注入（而非删掉让 BRBE 独占）是为了 <b>RBIP 配置独立加载时的鲁棒性</b>：
 * 只要两个入口里有一个活着，接缝就成立；同时它是幂等的——只有第一个能认领。</p>
 */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    private double getScaledXPos(Window window) {
        throw new AssertionError();
    }

    @Shadow
    private double getScaledYPos(Window window) {
        throw new AssertionError();
    }

    @Inject(at = @At("HEAD"), method = "onScroll", cancellable = true)
    private void rbip$scrollRecipeBookTabs(long windowHandle, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
        if (windowHandle != this.minecraft.getWindow().handle()) {
            return;
        }
        Window window = this.minecraft.getWindow();
        if (RecipeBookGesture.claimScroll(this.getScaledXPos(window), this.getScaledYPos(window), verticalAmount)) {
            ci.cancel();
        }
    }
}
