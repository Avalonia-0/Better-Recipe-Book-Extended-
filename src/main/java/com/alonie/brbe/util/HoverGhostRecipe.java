package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeButtonAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方书**悬停即预览**（用户 2026-09-25 诉求）：指针停在配方按钮上时，直接把该配方的
 * 幽灵物品写进功能方块的工作区（合成网格 / 熔炉料槽…），移开立刻还原。
 *
 * <p>写入走原版自己的 {@link RecipeBookComponent#fillGhostRecipe(RecipeDisplay)}
 * ——与「点击配方后服务端回包」最终调用的**同一个方法**，所以外观、红罩、轮循、
 * 逐物品折叠锁（{@code GhostSlotsCycleLockMixin}）全部自动一致，且**不需要服务端
 * 往返**（点击路径要点一下才出幽灵，正是因为幽灵来自回包）。</p>
 *
 * <h3>为什么需要"快照 / 接管"两个标志</h3>
 * <ul>
 *   <li><b>快照</b>：悬停 A 前可能已经有幽灵（例如刚点过 B）。悬停只是**预览层**，
 *       移开时应当还原到进入前的状态，而不是一把清空。</li>
 *   <li><b>接管（overridden）</b>：点击配方（{@code tryPlaceRecipe}）或服务端回包
 *       （{@link #invalidate()}）会重写 / 清空幽灵槽位——那之后幽灵的所有权就交回
 *       原版了，我们**绝不能**再拿旧快照覆盖（否则会把"点击后留下的缺料引导"抹掉，
 *       而玩家此时正要把鼠标移向工作区）。</li>
 * </ul>
 *
 * <p>逐帧由 {@code hoverghost/RecipeBookPageMixin}（配方书页按钮）与
 * {@code hoverghost/OverlayRecipeComponentMixin}（展开的替代配方组浮层）驱动：
 * 命中某个按钮就 {@link #hover}，一个都没命中就 {@link #release}。</p>
 */
public final class HoverGhostRecipe {

    /** 触发本次预览的按钮（身份比较，用于"同一按钮同一配方不重复写入"）。 */
    @Nullable
    private static Object hoverOwner;
    /** 当前预览的配方。 */
    @Nullable
    private static RecipeDisplay shown;
    /** 写入预览的组件（释放时用它取回 {@code GhostSlots}）。 */
    @Nullable
    private static RecipeBookComponent<?> activeBook;
    /** 预览前的幽灵快照（{@code Slot → GhostSlot} 条目副本）。 */
    @Nullable
    private static List<Object[]> snapshot;
    /** 当前显示的幽灵是不是我们写的。 */
    private static boolean previewing;
    /** 原版流程已接管幽灵槽位 → 释放时不许还原快照。 */
    private static boolean overridden;
    /** 自己的写入标记（{@code fillGhostRecipe} 的 mixin 据此区分"外部写入"）。 */
    private static boolean selfFill;

    private HoverGhostRecipe() {
    }

    /** 供 {@code hoverghost/RecipeBookComponentMixin} 区分自己人。 */
    public static boolean isSelfFill() {
        return selfFill;
    }

    /**
     * 逐帧命中：指针下的按钮要预览 {@code display} 的幽灵物品。
     *
     * @param book    当前界面的配方书组件
     * @param owner   命中按钮（身份用）
     * @param display 该按钮**当前轮循到**的配方（{@code null} = 释放）
     */
    public static void hover(@Nullable RecipeBookComponent<?> book, Object owner, @Nullable RecipeDisplay display) {
        if (book == null || display == null) {
            release();
            return;
        }
        if (previewing && owner == hoverOwner && display == shown) {
            return;
        }
        endPreview();
        GhostSlots slots = ghostSlots(book);
        if (slots == null) {
            return;
        }
        activeBook = book;
        hoverOwner = owner;
        shown = display;
        snapshot = snapshot(slots);
        overridden = false;
        previewing = true;
        install(book, display);
    }

    /** 逐帧未命中：撤下预览（还原快照，或什么都不做——见 {@link #invalidate()}）。 */
    public static void release() {
        hoverOwner = null;
        shown = null;
        endPreview();
    }

    /**
     * 原版流程（点击放置 / 服务端回包）接管了幽灵槽位：放弃还原权与所有权。
     * 之后 {@link #release()} 只是清掉内部状态，绝不触碰幽灵槽位。
     */
    public static void invalidate() {
        if (previewing) {
            overridden = true;
        }
        snapshot = null;
    }

    @Nullable
    public static RecipeDisplay displayOf(@Nullable RecipeDisplayId id) {
        if (id == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        RecipeDisplayEntry entry = ((ClientRecipeBookAccessor) mc.player.getRecipeBook()).brbe$getKnown().get(id);
        return entry == null ? null : entry.display();
    }

    /**
     * 页按钮**当前轮循到**的配方（未展开的替代配方组按钮 = 当前变体）。
     * {@code selectedEntries} 为空时原版 {@code getCurrentRecipe()} 会 /0，先挡掉。
     */
    @Nullable
    public static RecipeDisplay displayOf(@Nullable RecipeButton button) {
        if (button == null || ((RecipeButtonAccessor) button).brbe$getSelectedEntries().isEmpty()) {
            return null;
        }
        return displayOf(button.getCurrentRecipe());
    }

    /** 当前界面所属的配方书组件（替代配方组浮层用——它没有指回组件的引用）。 */
    @Nullable
    public static RecipeBookComponent<?> currentBook() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen instanceof AbstractRecipeBookScreen<?> recipeBookScreen) {
            return ((AbstractRecipeBookScreenAccessor) recipeBookScreen).brbe$getRecipeBookComponent();
        }
        return null;
    }

    private static void endPreview() {
        if (!previewing) {
            return;
        }
        previewing = false;
        List<Object[]> snap = snapshot;
        RecipeBookComponent<?> book = activeBook;
        snapshot = null;
        activeBook = null;
        if (overridden || snap == null || book == null) {
            return;
        }
        GhostSlots slots = ghostSlots(book);
        if (slots != null) {
            restore(slots, snap);
        }
    }

    private static void install(RecipeBookComponent<?> book, RecipeDisplay display) {
        selfFill = true;
        try {
            book.fillGhostRecipe(display);
        } finally {
            selfFill = false;
        }
    }

    @Nullable
    private static GhostSlots ghostSlots(RecipeBookComponent<?> book) {
        return ((RecipeBookComponentAccessor) book).getGhostSlots();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Object[]> snapshot(GhostSlots slots) {
        Reference2ObjectMap<Slot, ?> map = ((GhostSlotsAccessor) slots).getIngredients();
        List<Object[]> out = new ArrayList<>(map.size());
        for (Reference2ObjectMap.Entry<Slot, ?> entry : map.reference2ObjectEntrySet()) {
            out.add(new Object[]{entry.getKey(), entry.getValue()});
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restore(GhostSlots slots, List<Object[]> snap) {
        Reference2ObjectMap map = ((GhostSlotsAccessor) slots).getIngredients();
        map.clear();
        for (Object[] entry : snap) {
            map.put(entry[0], entry[1]);
        }
    }
}
