package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.CycleLock;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
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
 * 自己按槽位迭代，每次 accept 前后把该**容器槽位**（它的 {@code x/y} 就是屏幕
 * 坐标）连同矩形压进 {@link CycleLock} 的绘制上下文。共享的
 * {@code SlotSelectTime} 便在取值时逐物品判定：只有指针下这一个幽灵物品返回冻结
 * 下标，其余照常自动轮换。</p>
 *
 * <p>tooltip 路径（{@code extractTooltip}）单独注入：它同样按 {@code Slot} 取值，
 * 冻结期间鼠标提示必须与画出来的变体一致。</p>
 */
@Mixin(GhostSlots.class)
public abstract class GhostSlotsCycleLockMixin {

    /** 幽灵物品逐槽位绘制：每次回调前后压/弹该槽位的绘制上下文。 */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/Reference2ObjectMap;forEach(Ljava/util/function/BiConsumer;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void brbe$forEachWithCycleContext(Reference2ObjectMap map, BiConsumer consumer) {
        for (Object raw : map.reference2ObjectEntrySet()) {
            if (!(raw instanceof Reference2ObjectMap.Entry entry)) continue;
            Slot slot = (Slot) entry.getKey();
            CycleLock.pushContext(slot, slot.x, slot.y, 16, 16);
            try {
                consumer.accept(slot, entry.getValue());
            } finally {
                CycleLock.popContext();
            }
        }
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void brbe$pushTooltipContext(GuiGraphics gui, Minecraft minecraft,
                                         int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        CycleLock.pushContext(slot, slot.x, slot.y, 16, 16);
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void brbe$popTooltipContext(GuiGraphics gui, Minecraft minecraft,
                                        int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        CycleLock.popContext();
    }
}
