package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.CycleLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录幽灵配方**渲染原点**（容器左上角），供逐物品折叠锁做指针命中判定。
 *
 * <p>1.21.1 与 1.21.9+ 不同：没有 {@code SlotSelectTime}，幽灵物品的变体由
 * {@code GhostRecipe.time} 驱动（{@code GhostIngredient.getItem()} =
 * {@code items[floor(time / 30) % n]}），而且每个 {@code GhostIngredient} 的位置是
 * **容器相对坐标**（{@code Slot.x/y}）——真正的屏幕坐标 = 渲染原点 + 它。原点只在
 * {@code GhostRecipe.render(gui, mc, x, y, …)} 的参数里出现，所以这里在 HEAD 记下来
 * （{@link GhostRecipeOrigin} 暴露给 {@link GhostIngredientCycleLockMixin}）。</p>
 */
@Mixin(GhostRecipe.class)
public abstract class GhostRecipeCycleLockMixin implements GhostRecipeOrigin {

    @Unique
    private int brbe$originX;

    @Unique
    private int brbe$originY;

    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$captureOrigin(GuiGraphics gui, Minecraft minecraft, int x, int y,
                                    boolean isFirst, float delta, CallbackInfo ci) {
        brbe$originX = x;
        brbe$originY = y;
    }

    /**
     * 幽灵物品绘制结束：消费排队中的滚轮（锁定键 + 滚轮 → 逐格翻动指针下的幽灵物品）。
     *
     * <p>⚠️ 为什么不能只靠配方书页那一条分支（{@code scrollablepages/RecipeBookPageMixin}）：
     * 配方书页只在**书体可见**时绘制（{@code RecipeBookComponent.render} 开头就
     * {@code if (!isVisible()) return;}），而幽灵物品在书体收起后照样显示——1.21.1 的
     * {@code CraftingScreen/InventoryScreen.render} 调 {@code renderGhostRecipe} 是无条件的，
     * 而点击配方时原版又正好会把书体收起（{@code setVisible(false)}）。于是"指针停在
     * 幽灵物品上按锁定键+滚轮"在书体收起时完全没人消费队列（用户 2026-09-26 反馈：
     * 幽灵物品锁得住、滚轮翻不动）。这里跟着幽灵物品的绘制一起跑，与配方书页那条
     * 分支共用 {@link CycleLock#consumeQueuedScroll()}，谁先跑到谁消费。</p>
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$consumeQueuedScroll(GuiGraphics gui, Minecraft minecraft, int x, int y,
                                          boolean isFirst, float delta, CallbackInfo ci) {
        CycleLock.consumeQueuedScroll();
    }

    @Override
    public int brbe$originX() {
        return brbe$originX;
    }

    @Override
    public int brbe$originY() {
        return brbe$originY;
    }
}
