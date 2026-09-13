package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.CycleLock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配方书**网格按钮**（一个配方组多个变体时的结果图标轮换）的逐物品折叠锁
 * （1.21.1 专有挂钩，用户 2026-09-13 诉求 2）。
 *
 * <p>1.21.1 的 {@code RecipeButton} 用自己的 {@code time} 字段轮换图标
 * （{@code renderWidget} 里 {@code time += delta}，随后
 * {@code floor(time / TICKS_TO_SWAP) % n} 选变体）。这里在 RETURN 判定指针是否
 * 落在这**一个**按钮上：是 → 把 {@code time} 压回 {@link CycleLock} 记录的变体
 * （首次冻结 latch 住当时的自动下标，锁定键+滚轮逐格翻动它）；不是 → 原样自动
 * 轮换。旧实现是"按住锁定键整本配方书一起冻"，与用户要求相反。</p>
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonCycleLockMixin {

    @Shadow
    private float time;

    @Inject(method = "renderWidget", at = @At("RETURN"))
    private void brbe$holdCycle(GuiGraphics gui, int mouseX, int mouseY, float delta,
                                CallbackInfo ci) {
        AbstractWidget self = (AbstractWidget) (Object) this;
        Object key = this;
        if (!CycleLock.claimScreen(key, self.getX(), self.getY(),
                self.getWidth(), self.getHeight())) {
            CycleLock.release(key);
            return;
        }
        int autoIndex = (int) (this.time / RecipeButton.TICKS_TO_SWAP);
        int idx = CycleLock.indexFor(key, autoIndex);
        // 压回「冻结变体」对应的时刻：下一个帧的 floor(time / 30) 就是它，滚轮
        // 步进由 CycleLock 的冻结下标变化体现（消费方对 n 取模，越界安全）。
        this.time = idx * RecipeButton.TICKS_TO_SWAP;
    }
}
