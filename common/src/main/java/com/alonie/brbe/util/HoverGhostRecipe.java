package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.GhostIngredientAccessor;
import com.alonie.brbe.mixins.accessors.GhostRecipeAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeButtonAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
 *
 * <h3>工作区已经摆好这条配方 → 不预览</h3>
 * 用户 2026-09-27 收尾诉求：工作区里已经摆好了某个可合成配方的材料时，再悬停那条配方
 * **不再**尝试展示幽灵。否则预览会把摆好的真实物品整片藏掉、换成一份一模一样的幽灵
 * （看起来像"我的材料变成了幽灵"），而幽灵此时其实**一格也补不进去**。
 * 判据见 {@link #workspaceHasRecipe}——读原版刚写好的那份幽灵，逐条与工作区实物比对。
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
    /**
     * 悬停命中、但**工作区已经摆好这条配方** → 本次不预览（幽灵没东西可补）。
     * 与 {@link #previewing} 互斥；保留 {@code hoverOwner}/{@code shown} 身份，避免同一按钮上
     * 每帧重算（见 {@link #hover}）。
     */
    private static boolean suppressed;
    /**
     * 本悬停目标**已经交给原版**（点击放置 / 服务端回包）→ 直到悬停目标变化都不再预览。
     * 与 {@link #suppressed} 的区别是"谁做的决定"：这个由原版流程决定，见 {@link #invalidate()}。
     */
    private static boolean handedOver;
    /**
     * 交给原版的那条**配方**（身份）。点击后配方页会因槽位变化重建按钮对象，
     * 只按按钮身份判会漏掉"同一格、同一配方、新按钮"这种情况 → 预览又被装回去。
     * 自研书用布尔标记自行管理清除（{@link #markHandedOver()} 这里为 {@code null}）。
     */
    @Nullable
    private static RecipeHolder<?> handedOverRecipe;
    /** 原版流程已接管幽灵槽位 → 释放时不许还原快照。 */
    private static boolean overridden;
    /** 自己的写入标记（{@code setupGhostRecipe} 的 mixin 据此区分"外部写入"）。 */
    private static boolean selfFill;

    /** 本自研书（酿造/锻造）的悬停预览心跳时刻（0 = 无）；见 {@link #setGenericPreviewing}。 */
    private static long genericPreviewAt;


    /** 心跳有效期：界面关闭后不再有心跳，超时即视为"没有预览在显示"。 */
    private static final long PREVIEW_TTL_MS = 250L;

    private HoverGhostRecipe() {
    }

    /** 供 {@code hoverghost/RecipeBookComponentMixin} 区分自己人。 */
    public static boolean isSelfFill() {
        return selfFill;
    }

    /**
     * 悬停预览是否**正在本界面显示**——「暂隐工作区真实物品」（
     * {@code hoverghost/AbstractContainerScreenSlotMixin}）的判定入口。
     *
     * <p>两条来源：① 本类接管的工作台预览（{@code previewing}，再校验预览所属组件就是
     * 当前界面的组件——换界面后旧状态不能继续生效）；② BRBE 自研书（酿造/锻造）的预览，
     * 由 {@link #setGenericPreviewing} 逐帧打心跳（TTL 见 {@link #PREVIEW_TTL_MS}——
     * 界面关闭后不再有心跳，自动失效，不会把下个界面的物品也藏起来）。</p>
     */
    public static boolean isPreviewing() {
        if (genericPreviewAt != 0L && System.currentTimeMillis() - genericPreviewAt <= PREVIEW_TTL_MS) {
            return true;
        }
        return previewing && activeBook != null && activeBook == currentBook();
    }

    /**
     * BRBE 自研书（酿造/锻造）的悬停预览心跳：{@code GenericRecipeBookComponent} 每帧
     * 调用（可见时），{@code true} = 本帧有配方被悬停、预览正在显示。
     */
    /**
     * 这个槽位的真实物品是否应在预览期间被**暂隐**：预览显示时，**工作区的真实物品一律隐藏**
     * ——不管本次幽灵会不会画到它。
     *
     * <p>2026-09-26 用户反馈的缺陷：此前只隐藏"幽灵会画到的槽位"，于是幽灵**没覆盖**的槽位里
     * 的真实物品会留在画面上，和幽灵混在一起（例：幽灵只占左上角一格，其余格子里玩家放的
     * 东西原样显示）。现在只要预览在显示，容器侧（非玩家背包）的槽位全部隐藏——覆盖到的槽位
     * 显示幽灵、没覆盖的槽位保持空白，预览画面干净。</p>
     *
     * <p>⚠️ 之所以敢整片隐藏，是因为幽灵的绘制**不再依赖"槽位为空"**：原版
     * {@code GhostSlots} 本来就无条件画；BRBE 自研书由 {@code GenericGhostRecipe.render}
     * 在预览期间忽略渲染谓词（{@code hoverPreview}）强制画——否则会出现 2026-09-25 那个
     * "整格空白、连幽灵都没有"的回归。</p>
     */
    public static boolean hidesRealItemIn(@Nullable Slot slot) {
        if (slot == null || !isPreviewing()) {
            return false;
        }
        // 玩家背包/快捷栏/护甲/副手不是工作区（它们的 container 都是 Inventory）；
        // 其余（合成网格、锻造/酿造/熔炉料槽、结果槽…）都属于"工作区"，预览期间一律隐藏。
        return !(slot.container instanceof Inventory);
    }

    /**
     * BRBE 自研书（酿造/锻造）的悬停预览心跳：{@code GenericRecipeBookComponent} 每帧
     * 调用（可见时）。{@code value} = 本帧有配方被悬停、预览正在显示。
     */
    public static void setGenericPreviewing(boolean value) {
        genericPreviewAt = value ? System.currentTimeMillis() : 0L;
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
        boolean sameTarget = owner == hoverOwner && recipe == shown;
        // 已交给原版（点击放置 / 服务端回包）：同一格 **或同一配方**（按钮对象被页面重建）都不再预览
        if (handedOver && (sameTarget || handedOverRecipe == recipe)) {
            hoverOwner = owner;
            shown = recipe;
            return;
        }
        if (sameTarget && (previewing || suppressed)) {
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
        // 工作区**已经摆好这条配方**（幽灵一格也补不进去）→ 本次不预览（用户 2026-09-27 诉求）：
        // 归还幽灵槽位（还原快照）、把真实物品的显示权留着（不置 previewing，就不会"暂隐工作区
        // 真实物品"）；记下 suppressed 后同一按钮上不再每帧重算。
        if (workspaceHasRecipe(book)) {
            previewing = false;
            suppressed = true;
            restoreSnapshot();
            return;
        }
        suppressed = false;
    }

    /** 逐帧未命中：撤下预览（还原快照，或什么都不做——见 {@link #invalidate()}）。 */
    public static void release() {
        hoverOwner = null;
        shown = null;
        endPreview();
    }

    /**
     * 原版流程（点击放置 / 服务端回包）接管了幽灵：**预览当场结束**，放弃还原权与所有权，
     * 并把本悬停目标标记为"已交给原版"（{@link #isHandedOver()}）——鼠标不动也不再重新预览。
     *
     * <p>用户 2026-09-27 实测缺陷：点击可合成配方后真实物品**当场被藏起来**（工作区看起来空的），
     * 鼠标拿开再移回才恢复。成因两段：① 客户端点击（{@code tryPlaceRecipe}/{@code setupGhostRecipe}
     * 路径）先把幽灵清掉再发放置包；② 服务端只在材料不齐（{@code PostPlaceAction.PLACE_GHOST_RECIPE}）
     * 时才回幽灵包——材料齐全时**没有幽灵回包**。于是"幽灵空 + 悬停态仍算 previewing" ⇒
     * {@code isPreviewing()} 恒真 ⇒ 摆好的真实物品全被
     * {@code hoverghost/AbstractContainerScreenSlotMixin} 藏掉。此前这里只置 {@code overridden}
     * （管的是"别用旧快照覆盖"），没有结束预览本身，而 {@link #hover} 的身份短路又让下一帧
     * 直接 return、永远不重判。</p>
     */
    public static void invalidate() {
        if (previewing) {
            overridden = true;
        }
        // 预览当场结束：不再藏真实物品（幽灵的所有权已经在原版手里，我们什么都不还原）
        previewing = false;
        activeBook = null;
        snapshotIngredients = null;
        snapshotRecipe = null;
        // 所有权变了（点击放置 / 服务端回包）→ 抑制状态同样作废，悬停目标变化后用新的工作区内容重判
        suppressed = false;
        // 本悬停目标交给原版：直到悬停目标变化，不再预览（否则下一帧身份短路失效 → 又把刚放好的
        // 材料重新藏起来盖上一层我们自己写的幽灵）。记**配方身份**，页面重建按钮也不受影响。
        handedOver = true;
        handedOverRecipe = shown;
        // 点击放置/服务端回包接管幽灵 → 自研书的心跳一起清掉，
        // 否则最多 250ms 内「暂隐工作区真实物品」与"忽略渲染谓词"仍会作用在刚放好的材料上。
        genericPreviewAt = 0L;
    }

    /**
     * 本悬停目标是否已经交给原版（点击放置 / 服务端回包之后）——自研书（酿造/锻造）用：
     * 它们的预览状态由自己逐帧维护，同样需要"鼠标不动就别再预览"。
     */
    public static boolean isHandedOver() {
        return handedOver;
    }

    /** 登记"本悬停目标交给原版"（自研书的点击放置路径用；清理由 {@link #clearHandedOver()}）。 */
    public static void markHandedOver() {
        handedOver = true;
        handedOverRecipe = null;
    }

    /** 悬停目标变化 / 释放时清掉交接标记（{@link #endPreview()} 也会清）。 */
    public static void clearHandedOver() {
        handedOver = false;
        handedOverRecipe = null;
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
        suppressed = false;
        handedOver = false;
        handedOverRecipe = null;
        if (!previewing) {
            return;
        }
        previewing = false;
        restoreSnapshot();
    }

    /** 把幽灵槽位还原到进入预览前（快照）——没快照/已被原版接管则什么都不做。 */
    private static void restoreSnapshot() {
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

    /**
     * 工作区**已经摆好这条配方**——幽灵的每个**材料**条目在对应槽位里都已经是候选物品之一
     * （用户 2026-09-27 收尾诉求）。
     *
     * <p>1.21.1 的 {@code GhostRecipe.GhostIngredient} 只存 {@code (x, y)}、**不存槽位引用**，
     * 所以这里用"坐标 → 槽位"反查：原版 {@code PlaceRecipe} 就是把 {@code slot.x/slot.y} 传进
     * {@code addIngredient} 的，坐标与 {@code Slot.x/y} 同源。</p>
     *
     * <p><b>结果条目不参与判定</b>：原版 {@code setupGhostRecipe} **最先**加入结果条目
     * （位置取 {@code slots.get(0)}），熔炉的结果槽又要等烧炼完成才是满的——索引 0 与
     * "落在结果槽上的条目"两条都跳过，免得把结果当材料比。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean workspaceHasRecipe(RecipeBookComponent book) {
        GhostRecipe ghost = ghost(book);
        Minecraft mc = Minecraft.getInstance();
        if (ghost == null || mc.player == null) {
            return false;
        }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) {
            return false;
        }
        List<GhostRecipe.GhostIngredient> ingredients = ((GhostRecipeAccessor) ghost).getIngredients();
        if (ingredients.size() <= 1) {
            return false;
        }
        int resultIndex = menu instanceof RecipeBookMenu<?, ?> recipeBookMenu
                ? recipeBookMenu.getResultSlotIndex() : -1;
        boolean anyIngredient = false;
        for (int i = 0; i < ingredients.size(); i++) {
            if (i == 0) {
                continue;
            }
            GhostRecipe.GhostIngredient entry = ingredients.get(i);
            Slot slot = slotAt(menu, entry.getX(), entry.getY());
            if (slot == null) {
                return false;
            }
            if (slot.index == resultIndex) {
                continue;
            }
            anyIngredient = true;
            ItemStack real = slot.getItem();
            if (real.isEmpty()) {
                return false;
            }
            boolean matched = false;
            for (ItemStack candidate : ((GhostIngredientAccessor) entry).brbe$getIngredient().getItems()) {
                if (satisfies(real, candidate)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return anyIngredient;
    }

    /** 幽灵条目以容器相对坐标标识槽位（见 {@link #workspaceHasRecipe}）。 */
    @Nullable
    private static Slot slotAt(AbstractContainerMenu menu, int x, int y) {
        for (Slot slot : menu.slots) {
            if (slot.x == x && slot.y == y) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 工作区实物 {@code real} 是否已经满足某个幽灵候选 {@code candidate}：物品相同、数量不少于
     * 候选数量；候选**带组件**（药水、附魔、纹饰…）时组件也必须一致。
     *
     * <p>纯物品候选只比物品类型——与 {@code Ingredient} 的"任选其一"语义一致（"任意颜色的木板"
     * 这类候选不该被组件差异挡住）。自研书（锻造/酿造）的"已摆好"判定共用本方法。</p>
     */
    public static boolean satisfies(ItemStack real, ItemStack candidate) {
        if (real == null || candidate == null || real.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        if (real.getItem() != candidate.getItem() || real.getCount() < candidate.getCount()) {
            return false;
        }
        return candidate.getComponentsPatch().isEmpty()
                || ItemStack.isSameItemSameComponents(real, candidate);
    }
}
