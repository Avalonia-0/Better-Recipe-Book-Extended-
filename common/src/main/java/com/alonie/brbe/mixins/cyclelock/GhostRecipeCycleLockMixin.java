package com.alonie.brbe.mixins.cyclelock;

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

    @Override
    public int brbe$originX() {
        return brbe$originX;
    }

    @Override
    public int brbe$originY() {
        return brbe$originY;
    }
}
