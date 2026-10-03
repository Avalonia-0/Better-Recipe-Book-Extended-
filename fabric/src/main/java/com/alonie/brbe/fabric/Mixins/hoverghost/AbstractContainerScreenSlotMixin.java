package com.alonie.brbe.fabric.Mixins.hoverghost;

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
 * 悬停预览期间**暂时隐藏工作区里的真实物品**（用户 2026-09-25 诉求）——**Fabric / 原版**版。
 *
 * <p>⚠️ <b>为什么要按加载器拆成两个 mixin</b>（2026-09-27 冒烟测试发现）：NeoForge 21.1 把
 * 原版 {@code AbstractContainerScreen.renderSlot} 里的物品绘制抽成了
 * {@code renderSlotContents(GuiGraphics, ItemStack, Slot, String)} —— 原版 {@code renderSlot}
 * 里那两条 {@code GuiGraphics.renderItem/renderFakeItem} 调用在 NeoForge 上**不在 renderSlot
 * 内**。原先放在 {@code mixins.brbe-common.json} 的 {@code @Redirect(method="renderSlot")}
 * 在 NeoForge 上扫到 0 个目标 → {@code Critical injection failure} → <b>启动即崩</b>
 * （Fabric 正常，因为原版就是那个结构）。NeoForge 版见
 * {@code com.alonie.brbe.neoforge.Mixins.hoverghost.AbstractContainerScreenSlotContentsMixin}。</p>
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

    /** 正在绘制的槽位（{@code renderSlot} 内）——{@code @Redirect} 拿不到 Slot 参数，
     *  这里在方法头尾记一下，供隐藏判定取。 */
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

    /** 真实物品本体（原版两条分支：普通槽 / 伪槽）。 */
    @Redirect(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void brbe$hideRealItem(GuiGraphics gui, ItemStack stack, int x, int y, int seed) {
        if (brbe$hideRealItems()) return;
        gui.renderItem(stack, x, y, seed);
    }

    @Redirect(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void brbe$hideRealFakeItem(GuiGraphics gui, ItemStack stack, int x, int y, int seed) {
        if (brbe$hideRealItems()) return;
        gui.renderFakeItem(stack, x, y, seed);
    }

    /** 数量角标 / 耐久条：物品都藏了再留个"×4"会很怪。 */
    @Redirect(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void brbe$hideRealItemDecorations(GuiGraphics gui, Font font, ItemStack stack,
                                              int x, int y, String count) {
        if (brbe$hideRealItems()) return;
        gui.renderItemDecorations(font, stack, x, y, count);
    }

    @Unique
    private static boolean brbe$hideRealItems() {
        // 预览显示期间工作区真实物品一律隐藏（玩家背包除外）——见 HoverGhostRecipe 的说明。
        return HoverGhostRecipe.hidesRealItemIn(brbe$drawingSlot);
    }
}
