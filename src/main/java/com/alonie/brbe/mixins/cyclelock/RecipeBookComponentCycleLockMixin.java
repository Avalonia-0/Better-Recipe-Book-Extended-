package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.CycleLockSlotSelectTime;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.SlotSelectTime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 把原版配方书构造器里交给 {@code GhostSlots} / {@code RecipeBookPage} 的
 * {@link SlotSelectTime} 换成 {@link CycleLockSlotSelectTime}：这样「锁定折叠
 * 物品」键一按住，**配方书网格按钮的配方图标轮换**与**功能方块里的幽灵物品
 * 轮换**（两者共用这一个 SlotSelectTime）都会冻结，松开恢复（用户
 * 2026-09-13 诉求 1）。
 *
 * <p>挂在这里而不是逐个 mixin {@code RecipeButton} / {@code GhostSlots}：原版
 * 的 `SlotSelectTime` 实现是构造器里的一个 lambda（{@code lambda$new$0}，方法名
 * 由编译器生成、跨版本不稳），而 {@code GhostSlots}/{@code RecipeBookPage} 的
 * 构造器签名是稳定的公开 API，{@code @ModifyArg} 只改传入的那个参数。</p>
 *
 * <p>滚轮逐格翻动在 {@code scrollablepages/RecipeBookPageMixin} 里消费
 * {@code queuedScroll} 的分支处理（锁定键按住时改为步进而不是翻页）。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentCycleLockMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/GhostSlots;<init>(Lnet/minecraft/client/gui/screens/recipebook/SlotSelectTime;)V"))
    private SlotSelectTime brbe$lockGhostSlotCycle(SlotSelectTime original) {
        return new CycleLockSlotSelectTime(original);
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;<init>(Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;Lnet/minecraft/client/gui/screens/recipebook/SlotSelectTime;Z)V"),
            index = 1)
    private SlotSelectTime brbe$lockPageCycle(SlotSelectTime original) {
        return new CycleLockSlotSelectTime(original);
    }
}
