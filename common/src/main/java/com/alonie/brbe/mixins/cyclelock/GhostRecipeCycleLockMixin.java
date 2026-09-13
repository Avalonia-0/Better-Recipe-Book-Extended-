package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 功能方块里的**幽灵物品**轮换的折叠锁（1.21.1 专有挂钩）。
 *
 * <p>1.21.1 没有 1.21.9+ 的 {@code SlotSelectTime}：幽灵物品的变体由
 * {@code GhostRecipe.time} 驱动（{@code GhostIngredient.getItem()} =
 * {@code items[floor(time / 30) % n]}），而 {@code render} 每帧把
 * {@code time += delta}（Ctrl 按住时原版自己会跳过推进）。这里在 RETURN 把
 * {@code time} 压回「按下锁定键那一刻的值 + 滚轮步进 × 30」——冻结与逐格翻动
 * 都由这一处实现（用户 2026-09-13 诉求 1）。</p>
 */
@Mixin(GhostRecipe.class)
public abstract class GhostRecipeCycleLockMixin {

    @Shadow
    private float time;

    @Unique
    private float brbe$frozenTime;

    @Unique
    private boolean brbe$frozen;

    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$holdCycle(GuiGraphics gui, Minecraft minecraft, int x, int y,
                                boolean isFirst, float delta, CallbackInfo ci) {
        if (RecipeViewerOverlay.bookCycleLocked()) {
            if (!brbe$frozen) {
                brbe$frozen = true;
                brbe$frozenTime = this.time;
            }
            this.time = brbe$frozenTime + RecipeViewerOverlay.bookCycleSteps() * 30.0F;
        } else if (brbe$frozen) {
            brbe$frozen = false;
        }
    }
}
