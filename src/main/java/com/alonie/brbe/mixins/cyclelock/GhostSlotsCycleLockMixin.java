package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.util.CycleLock;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * 功能方块里**幽灵物品**的逐物品折叠锁（用户 2026-09-13 诉求 2）。
 *
 * <p>幽灵物品与网格按钮共用同一个 {@code SlotSelectTime}（原版把同一个实例交给
 * 两者），而幽灵物品的取值点在 {@code extractRenderState} 的逐槽位循环里——
 * 循环本体是一个编译器生成的 lambda（名字跨版本不稳），所以这里改
 * {@code @Redirect} 它外层的 {@code Reference2ObjectMap.forEach(...)} 调用：
 * 自己按槽位迭代，每次 accept 前后把该**容器槽位**连同它的**屏幕**矩形压进
 * {@link CycleLock} 的绘制上下文。共享的 {@code SlotSelectTime} 便在取值时逐物品
 * 判定：只有指针下这一个幽灵物品返回冻结下标，其余照常自动轮换。</p>
 *
 * <p>⚠️ 槽位的 {@code x/y} 是**容器相对**坐标，必须补上容器原点（{@link #brbe$containerOrigin()}）：
 * 原版 {@code AbstractContainerScreen.extractContents} 先把 pose 平移
 * {@code (leftPos, topPos)} 再画槽位与幽灵物品，而指针是屏幕坐标。2026-09-26 实测
 * 未补原点时命中判定永远失败——日志里幽灵槽位矩形 {@code (30,17)}、指针
 * {@code (350,118)}，其实是同一格（{@code leftPos=307, topPos=101} → 真实屏幕矩形
 * {@code (337,118,16,16)}），于是「Alt 锁不住、也翻不动」。</p>
 *
 * <p>tooltip 路径（{@code extractTooltip}）单独注入：它同样按 {@code Slot} 取值，
 * 冻结期间鼠标提示必须与画出来的变体一致。</p>
 */
@Mixin(GhostSlots.class)
public abstract class GhostSlotsCycleLockMixin {

    /** 幽灵物品所在容器界面的原点（{@code leftPos/topPos}）；不是容器界面时退回 (0,0)。 */
    @Unique
    private static int[] brbe$containerOrigin() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return new int[2];
        }
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
        return new int[] {accessor.brbe$getLeftPos(), accessor.brbe$getTopPos()};
    }

    /** 幽灵物品逐槽位绘制：每次回调前后压/弹该槽位的绘制上下文。 */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/Reference2ObjectMap;forEach(Ljava/util/function/BiConsumer;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void brbe$forEachWithCycleContext(Reference2ObjectMap map, BiConsumer consumer) {
        int[] origin = brbe$containerOrigin();
        for (Object raw : map.reference2ObjectEntrySet()) {
            if (!(raw instanceof Reference2ObjectMap.Entry entry)) continue;
            Slot slot = (Slot) entry.getKey();
            if (slot == null) {
                consumer.accept(null, entry.getValue());
                continue;
            }
            CycleLock.pushContext(slot, origin[0] + slot.x, origin[1] + slot.y, 16, 16);
            try {
                consumer.accept(slot, entry.getValue());
            } finally {
                CycleLock.popContext();
            }
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"))
    private void brbe$pushTooltipContext(GuiGraphicsExtractor gui, Minecraft minecraft,
                                         int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        // ⚠️ 原版在**没有悬停槽位**时传 slot == null（方法开头就 `if (slot == null) return;`）。
        // 2026-09-13 卡死/崩：这里直接读 slot.x → NPE → 渲染屏幕时抛 ReportedException。
        // 空上下文 = 不做逐物品判定（resolveContext 原样透传），返回时照样弹栈。
        if (slot == null) {
            CycleLock.pushContext(null, 0, 0, 0, 0);
            return;
        }
        int[] origin = brbe$containerOrigin();
        CycleLock.pushContext(slot, origin[0] + slot.x, origin[1] + slot.y, 16, 16);
    }

    @Inject(method = "extractTooltip", at = @At("RETURN"))
    private void brbe$popTooltipContext(GuiGraphicsExtractor gui, Minecraft minecraft,
                                        int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        CycleLock.popContext();
    }

    /**
     * 幽灵物品绘制结束：消费排队中的滚轮（锁定键 + 滚轮 → 逐格翻动指针下的幽灵物品）。
     *
     * <p>⚠️ 为什么不能只靠配方书页那一条分支（{@code scrollablepages/RecipeBookPageMixin}）：
     * 配方书页只在**书体可见**时绘制（{@code RecipeBookComponent.extractRenderState}
     * 开头就 {@code if (!isVisible()) return;}），而幽灵物品在书体收起后照样显示——
     * 原版 {@code AbstractRecipeBookScreen.extractSlots} 调 {@code extractGhostRecipe}
     * 是无条件的，而点击配方时原版又正好会把书体收起（{@code setVisible(false)}）。
     * 于是"指针停在幽灵物品上按锁定键+滚轮"在书体收起时完全没人消费队列（用户
     * 2026-09-26 反馈：幽灵物品锁得住、滚轮翻不动）。这里跟着幽灵物品的绘制一起跑，
     * 与配方书页那条分支共用 {@link CycleLock#consumeQueuedScroll()}，谁先跑到谁消费。</p>
     */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void brbe$consumeQueuedScroll(GuiGraphicsExtractor gui, Minecraft minecraft,
                                          boolean bl, CallbackInfo ci) {
        CycleLock.consumeQueuedScroll();
    }
}
