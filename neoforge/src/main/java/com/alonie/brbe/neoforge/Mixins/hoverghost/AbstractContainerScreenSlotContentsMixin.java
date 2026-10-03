package com.alonie.brbe.neoforge.Mixins.hoverghost;

import com.alonie.brbe.util.HoverGhostRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 悬停预览期间**暂时隐藏工作区里的真实物品**（用户 2026-09-25 诉求）——**NeoForge** 版。
 *
 * <p>与 Fabric 版（{@code com.alonie.brbe.fabric.Mixins.hoverghost.AbstractContainerScreenSlotMixin}）
 * 行为完全一致，唯一区别是重定向挂在哪：NeoForge 21.1 把原版 {@code renderSlot} 里的物品绘制
 * 抽成了 {@code renderSlotContents(GuiGraphics, ItemStack, Slot, String)}，原版的
 * {@code GuiGraphics.renderItem / renderFakeItem / renderItemDecorations} 三条调用在 NeoForge 上
 * 位于 <b>{@code renderSlotContents}</b> 内（用 javap 比对过两边的字节码：原版在 {@code renderSlot}
 * 偏移 444/458/473，NeoForge 在 {@code renderSlotContents} 偏移 57/72/88）。</p>
 *
 * <p>槽位跟踪仍在 {@code renderSlot} 上注入：NeoForge 的 {@code renderSlot} 只多了一层
 * {@code renderSlotContents} 调用，HEAD/RETURN 依旧把整次绘制夹在中间。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotContentsMixin {

    /** 正在绘制的槽位（{@code renderSlot} 内）——{@code @Redirect} 拿不到 Slot 参数。 */
    @Unique
    private static Slot brbe$drawingSlot;

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void brbe$beginSlotDraw(GuiGraphics gui, Slot slot, CallbackInfo ci) {
        brbe$drawingSlot = slot;
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void brbe$endSlotDraw(GuiGraphics gui, Slot slot, CallbackInfo ci) {
        brbe$drawingSlot = null;
    }

    /** 真实物品本体（NeoForge 的 renderSlotContents 里两条分支：伪槽 / 普通槽）。 */
    @Redirect(method = "renderSlotContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void brbe$hideRealItem(GuiGraphics gui, ItemStack stack, int x, int y, int seed) {
        if (brbe$hideRealItems()) return;
        gui.renderItem(stack, x, y, seed);
    }

    @Redirect(method = "renderSlotContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void brbe$hideRealFakeItem(GuiGraphics gui, ItemStack stack, int x, int y, int seed) {
        if (brbe$hideRealItems()) return;
        gui.renderFakeItem(stack, x, y, seed);
    }

    /** 数量角标 / 耐久条：物品都藏了再留个"×4"会很怪。 */
    @Redirect(method = "renderSlotContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void brbe$hideRealItemDecorations(GuiGraphics gui, Font font, ItemStack stack,
                                              int x, int y, String count) {
        if (brbe$hideRealItems()) return;
        gui.renderItemDecorations(font, stack, x, y, count);
    }

    @Unique
    private static boolean brbe$hideRealItems() {
        return HoverGhostRecipe.hidesRealItemIn(brbe$drawingSlot);
    }
}
