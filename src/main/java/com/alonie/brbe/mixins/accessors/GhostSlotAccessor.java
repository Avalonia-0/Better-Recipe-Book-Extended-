package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 读**单个**原版幽灵条目的内容（{@code GhostSlots$GhostSlot} 是**包私有 record**，外部拿不到
 * 类型，只能靠 accessor）。
 *
 * <p>用途：{@code HoverGhostRecipe} 的「工作区已经摆好这条配方 → 不预览」判定——把原版刚写好的
 * 幽灵逐条与工作区实物比对，比"自己按造型/无序/熔炉各写一遍映射"稳（映射由原版
 * {@code fillGhostRecipe} 产出，不会与版本漂移）。</p>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.GhostSlots$GhostSlot")
public interface GhostSlotAccessor {

    /** 该槽位的候选物品（原版 {@code SlotDisplay.resolveForStacks} 的结果，通常只有 1 件）。 */
    @Invoker("items")
    List<ItemStack> brbe$getItems();

    /** 该条目是不是**结果槽**（不参与"已摆好"判定：熔炉结果槽要等烧炼完成才是满的）。 */
    @Invoker("isResultSlot")
    boolean brbe$isResultSlot();
}
