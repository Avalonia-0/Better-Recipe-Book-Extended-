package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.GhostRecipeAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeButtonAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方书**悬停即预览**（用户 2026-09-25 诉求）：指针停在配方按钮上时，直接把该配方的
 * 幽灵物品写进功能方块的工作区（合成网格 / 熔炉料槽…），移开立刻还原。
 *
 * <p>写入走原版自己的 {@link RecipeBookComponent#setupGhostRecipe(RecipeHolder, List)}
 * ——与「点击配方后服务端回包」最终调用的**同一个方法**（{@code ClientPacketListener
 * .handlePlaceRecipe} 传的是 {@code player.containerMenu.slots}），所以外观、红罩、
 * 轮循全部自动一致，且**不需要服务端往返**。1.21.1 的幽灵是 {@code GhostRecipe}
 * 旧结构（1.21.2+ 才换成 {@code GhostSlots}）。</p>
 *
 * <h3>为什么需要"快照 / 接管"两个标志</h3>
 * <ul>
 *   <li><b>快照</b>：悬停 A 前可能已经有幽灵（例如刚点过 B）。悬停只是**预览层**，
 *       移开时应当还原到进入前的状态，而不是一把清空。</li>
 *   <li><b>接管（overridden）</b>：点击配方（{@code mouseClicked} 里的
 *       {@code ghostRecipe.clear()}）或服务端回包（{@link #invalidate()}）会重写 /
 *       清空幽灵——那之后幽灵的所有权就交回原版了，我们**绝不能**再拿旧快照覆盖
 *       （否则会把"点击后留下的缺料引导"抹掉，而玩家此时正要把鼠标移向工作区）。</li>
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
    private static RecipeHolder<?> shown;
    /** 写入预览的组件（释放时用它取回 {@code GhostRecipe}）。 */
    @Nullable
    private static RecipeBookComponent activeBook;
    /** 预览前的幽灵快照（幽灵条目 + 配方，均为不可变对象引用）。 */
    @Nullable
    private static List<GhostRecipe.GhostIngredient> snapshotIngredients;
    @Nullable
    private static RecipeHolder<?> snapshotRecipe;
    /** 当前显示的幽灵是不是我们写的。 */
    private static boolean previewing;
    /** 原版流程已接管幽灵槽位 → 释放时不许还原快照。 */
    private static boolean overridden;
    /** 自己的写入标记（{@code setupGhostRecipe} 的 mixin 据此区分"外部写入"）。 */
    private static boolean selfFill;

    private HoverGhostRecipe() {
    }

    /** 供 {@code hoverghost/RecipeBookComponentMixin} 区分自己人。 */
    public static boolean isSelfFill() {
        return selfFill;
    }

    /**
     * 配置「自动填充幽灵配方」（{@code BrbeConfig.autoFillGhostRecipe}，默认开）：
     * 关闭时悬停完全不出幽灵，也不会接管任何槽位——已经显示的预览立刻撤下。
     *
     * <p>唯一判定入口：合成台（本类）与酿造/锻造台
     * （{@code GenericRecipeBookComponent.brbe$updateHoverGhost}）共用它。</p>
     */
    public static boolean enabled() {
        return BetterRecipeBook.config != null && BetterRecipeBook.config.autoFillGhostRecipe;
    }

    /**
     * 逐帧命中：指针下的按钮要预览 {@code recipe} 的幽灵物品。
     *
     * @param book   当前界面的配方书组件
     * @param owner  命中按钮（身份用）
     * @param recipe 该按钮**当前轮循到**的配方（{@code null} = 释放）
     */
    public static void hover(@Nullable RecipeBookComponent book, Object owner, @Nullable RecipeHolder<?> recipe) {
        if (!enabled()) {
            release();
            return;
        }
        if (book == null || recipe == null) {
            release();
            return;
        }
        if (previewing && owner == hoverOwner && recipe == shown) {
            return;
        }
        endPreview();
        GhostRecipe ghost = ghost(book);
        if (ghost == null) {
            return;
        }
        activeBook = book;
        hoverOwner = owner;
        shown = recipe;
        snapshotRecipe = ghost.getRecipe();
        snapshotIngredients = new ArrayList<>(((GhostRecipeAccessor) ghost).getIngredients());
        overridden = false;
        previewing = true;
        install(book, recipe);
    }

    /** 逐帧未命中：撤下预览（还原快照，或什么都不做——见 {@link #invalidate()}）。 */
    public static void release() {
        hoverOwner = null;
        shown = null;
        endPreview();
    }

    /**
     * 原版流程（点击放置 / 服务端回包）接管了幽灵：放弃还原权与所有权。
     * 之后 {@link #release()} 只是清掉内部状态，绝不触碰幽灵。
     */
    public static void invalidate() {
        if (previewing) {
            overridden = true;
        }
        snapshotIngredients = null;
        snapshotRecipe = null;
    }

    /**
     * 页按钮**当前轮循到**的配方（未展开的替代配方组按钮 = 当前变体）。
     *
     * <p>不用原版 {@code RecipeButton.getRecipe()}：它直接 {@code list.get(currentIndex)}，
     * 列表内容在两帧之间变化时（配方解锁/过滤刷新）会越界。这里按当前下标取模。</p>
     */
    @Nullable
    public static RecipeHolder<?> recipeOf(@Nullable RecipeButton button) {
        if (button == null) {
            return null;
        }
        RecipeButtonAccessor accessor = (RecipeButtonAccessor) button;
        List<RecipeHolder<?>> ordered = accessor.brbe$getOrderedRecipes();
        if (ordered.isEmpty()) {
            return null;
        }
        return ordered.get(Math.floorMod(accessor.brbe$getCurrentIndex(), ordered.size()));
    }

    /** 当前界面所属的配方书组件（页/替代配方组浮层都不持有它）。 */
    @Nullable
    public static RecipeBookComponent currentBook() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen instanceof RecipeUpdateListener listener) {
            return listener.getRecipeBookComponent();
        }
        return null;
    }

    private static void endPreview() {
        if (!previewing) {
            return;
        }
        previewing = false;
        List<GhostRecipe.GhostIngredient> snapIngredients = snapshotIngredients;
        RecipeHolder<?> snapRecipe = snapshotRecipe;
        RecipeBookComponent book = activeBook;
        snapshotIngredients = null;
        snapshotRecipe = null;
        activeBook = null;
        if (overridden || snapIngredients == null || book == null) {
            return;
        }
        GhostRecipe ghost = ghost(book);
        if (ghost == null) {
            return;
        }
        // 直接改列表（不走 clear()）：clear() 会把轮循计时也归零，还原时会造成跳变。
        List<GhostRecipe.GhostIngredient> live = ((GhostRecipeAccessor) ghost).getIngredients();
        live.clear();
        live.addAll(snapIngredients);
        ghost.setRecipe(snapRecipe);
    }

    private static void install(RecipeBookComponent book, RecipeHolder<?> recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        selfFill = true;
        try {
            book.setupGhostRecipe(recipe, mc.player.containerMenu.slots);
        } finally {
            selfFill = false;
        }
    }

    @Nullable
    private static GhostRecipe ghost(RecipeBookComponent book) {
        return ((RecipeBookComponentAccessor) book).getGhostRecipe();
    }
}
