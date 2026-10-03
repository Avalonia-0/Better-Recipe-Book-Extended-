package com.alonie.brbe.mixins.hoverghost;

import com.alonie.brbe.util.HoverGhostRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * 悬停预览期间**暂时隐藏工作区里的真实物品**（用户 2026-09-25 诉求）。
 *
 * <p>「自动填充幽灵配方」把幽灵物品写进工作区，但槽里原有的真实物品还在下面：
 * 幽灵物品盖在上面、数量角标露在外面，预览看起来是"配方 + 我自己的东西"混在一起。
 * 诉求是预览时把它清干净——<b>只是画面隐藏</b>：不移动、不清空任何槽位数据，
 * 指针移开（预览撤下）立刻恢复显示。</p>
 *
 * <p>判定（{@link #brbe$hideRealItems()}）：预览正在显示（{@link HoverGhostRecipe#isPreviewing()}）
 * 时，<b>工作区的真实物品一律隐藏</b>（{@link HoverGhostRecipe#hidesRealItemIn}，玩家背包/护甲/
 * 副手除外）——2026-09-26 用户指出的缺陷：原实现只隐藏"会被幽灵盖住"的槽位，于是幽灵**覆盖不到**
 * 的格子里的真实物品会留在画面上与幽灵混在一起。幽灵的绘制不依赖"槽位为空"（原版无条件画、
 * 自研书在预览期间忽略渲染谓词），所以整片隐藏不会造成空格子。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotMixin {

    /** 正在绘制的槽位（{@code extractSlot} 内）——{@code @Redirect} 拿不到 Slot 参数，
     *  这里在方法头尾记一下，供隐藏判定取。 */
    @Unique
    private static Slot brbe$drawingSlot;

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void brbe$beginSlotDraw(GuiGraphicsExtractor gui, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        brbe$drawingSlot = slot;
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void brbe$endSlotDraw(GuiGraphicsExtractor gui, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        brbe$drawingSlot = null;
    }

    /** 真实物品本体（原版两条分支：普通槽 / 伪槽）。 */
    @Redirect(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void brbe$hideRealItem(GuiGraphicsExtractor gui, ItemStack stack, int x, int y, int seed) {
        if (brbe$hideRealItems()) return;
        gui.item(stack, x, y, seed);
    }

    @Redirect(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void brbe$hideRealFakeItem(GuiGraphicsExtractor gui, ItemStack stack, int x, int y, int seed) {
        if (brbe$hideRealItems()) return;
        gui.fakeItem(stack, x, y, seed);
    }

    /** 数量角标 / 耐久条：物品都藏了再留个"×4"会很怪。 */
    @Redirect(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void brbe$hideRealItemDecorations(GuiGraphicsExtractor gui, Font font, ItemStack stack,
                                              int x, int y, String count) {
        if (brbe$hideRealItems()) return;
        gui.itemDecorations(font, stack, x, y, count);
    }

    @Unique
    private static boolean brbe$hideRealItems() {
        // 预览显示期间工作区真实物品一律隐藏（玩家背包除外）——见 HoverGhostRecipe 的说明。
        return HoverGhostRecipe.hidesRealItemIn(brbe$drawingSlot);
    }
}
