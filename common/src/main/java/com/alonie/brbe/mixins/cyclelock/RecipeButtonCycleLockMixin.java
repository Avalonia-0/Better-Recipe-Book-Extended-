package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配方书**网格按钮**（一个配方组多个变体时的结果图标轮换）的折叠锁
 * （1.21.1 专有挂钩）。
 *
 * <p>1.21.1 的 {@code RecipeButton} 用自己的 {@code time} 字段轮换图标
 * （{@code renderWidget} 里 {@code time += delta}，随后
 * {@code floor(time / TICKS_TO_SWAP) % n} 选变体）。这里在 RETURN 把
 * {@code time} 压回「按下锁定键那一刻的值 + 滚轮步进 × TICKS_TO_SWAP」，
 * 实现与 26.2/1.21.11 的 {@code SlotSelectTime} 包装同等的效果
 * （用户 2026-09-13 诉求 1）。</p>
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonCycleLockMixin {

    @Shadow
    private float time;

    @Unique
    private float brbe$frozenTime;

    @Unique
    private boolean brbe$frozen;

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void brbe$holdCycle(GuiGraphics gui, int mouseX, int mouseY, float delta,
                                CallbackInfo ci) {
        if (RecipeViewerOverlay.bookCycleLocked()) {
            if (!brbe$frozen) {
                brbe$frozen = true;
                brbe$frozenTime = this.time;
            }
            this.time = brbe$frozenTime + RecipeViewerOverlay.bookCycleSteps() * RecipeButton.TICKS_TO_SWAP;
        } else if (brbe$frozen) {
            brbe$frozen = false;
        }
    }
}
