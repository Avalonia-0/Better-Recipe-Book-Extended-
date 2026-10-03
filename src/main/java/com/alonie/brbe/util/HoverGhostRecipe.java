package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.GhostSlotAccessor;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
 *
 * <h3>工作区已经摆好这条配方 → 不预览</h3>
 * 用户 2026-09-27 收尾诉求：工作区里已经摆好了某个可合成配方的材料时，再悬停那条配方
 * **不再**尝试展示幽灵。否则预览会把摆好的真实物品整片藏掉、换成一份一模一样的幽灵
 * （看起来像"我的材料变成了幽灵"），而幽灵此时其实**一格也补不进去**。
 * 判据见 {@link #workspaceHasRecipe}——直接读原版刚写好的那份幽灵，逐条与工作区实物比对。
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
    private static RecipeDisplay handedOverDisplay;
    /** 原版流程已接管幽灵槽位 → 释放时不许还原快照。 */
    private static boolean overridden;
    /** 自己的写入标记（{@code fillGhostRecipe} 的 mixin 据此区分"外部写入"）。 */
    private static boolean selfFill;

    /** 本自研书（酿造/锻造）的悬停预览心跳时刻（0 = 无）；见 {@link #setGenericPreviewing}。 */
    private static long genericPreviewAt;

    /** 心跳有效期：界面关闭后不再有心跳，超时即视为"没有预览在显示"。 */
    private static final long PREVIEW_TTL_MS = 250L;

    /**
     * 交接保持（用户 2026-09-27 反馈的"空窗期"）：点击可合成配方后，幽灵**不立刻撤下**，
     * 一直留到真实物品真的进了工作区。
     *
     * <p>成因：真实物品是**服务端**放的（客户端只发一个包 / 一串点击包），本机也要一两个 tick
     * 才在客户端的槽位里出现；而点击那一刻幽灵已经被撤掉 → 中间这段时间工作区**什么都没有**，
     * 观感是"配方闪了一下就空了"。保持期间真实物品照旧被暂隐（{@link #hidesRealItemIn}），
     * 所以物品到位瞬间是"幽灵换成实物"，看不出接缝。</p>
     */
    private static boolean holding;
    /** 交接保持的起始时刻（TTL 兜底）。 */
    private static long heldSince;
    /** 交接保持的配方：原版 {@code tryPlaceRecipe} 会先清空幽灵槽位，需要按它写回来。 */
    @Nullable
    private static RecipeDisplay heldDisplay;
    /**
     * 交接保持的最长时间。本机放置一两个 tick 就到；超时说明这次放置没有发生
     * （服务端拒绝 / 配方已失效），交还显示权并把幽灵清掉。
     */
    private static final long HOLD_TTL_MS = 600L;

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
        // ⚠️ 临时诊断（2026-09-26，BRBE-GHOST）：心跳翻转的时刻（"预览是否在显示"的权威信号）
        if (value != genericPreviewLast) {
            BrbeLogger.log("BRBE-GHOST", "preview-heartbeat={}", value);
            genericPreviewLast = value;
        }
        genericPreviewAt = value ? System.currentTimeMillis() : 0L;
    }

    /** ⚠️ 临时诊断用：上一次的心跳值（见 {@link #setGenericPreviewing}）。 */
    private static boolean genericPreviewLast;

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
     * 逐帧命中：指针下的按钮要预览 {@code display} 的幽灵物品。
     *
     * @param book    当前界面的配方书组件
     * @param owner   命中按钮（身份用）
     * @param display 该按钮**当前轮循到**的配方（{@code null} = 释放）
     */
    public static void hover(@Nullable RecipeBookComponent<?> book, Object owner, @Nullable RecipeDisplay display) {
        tickHandover();
        if (holding) {
            // 交接保持期间：预览状态由 beginPlacementHandover 维持；这里只跟上身份，别重装/释放
            hoverOwner = owner;
            shown = display;
            return;
        }
        if (!enabled()) {
            release();
            return;
        }
        if (book == null || display == null) {
            release();
            return;
        }
        boolean sameTarget = owner == hoverOwner && display == shown;
        // 已交给原版（点击放置 / 服务端回包）：同一格 **或同一配方**（按钮对象被页面重建）都不再预览
        if (handedOver && (sameTarget || handedOverDisplay == display)) {
            hoverOwner = owner;
            shown = display;
            return;
        }
        if (sameTarget && (previewing || suppressed)) {
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
        // 工作区**已经摆好这条配方**（幽灵一格也补不进去）→ 本次不预览（用户 2026-09-27 诉求）：
        // 撤销这次写入、把真实物品的显示权留着（不置 previewing，就不会"暂隐工作区真实物品"）；
        // 记下 suppressed 后同一按钮上不再每帧重算。
        if (workspaceHasRecipe(slots)) {
            previewing = false;
            suppressed = true;
            restore(slots, snapshot);
            snapshot = null;
            activeBook = null;
            return;
        }
        suppressed = false;
    }

    /** 逐帧未命中：撤下预览（还原快照，或什么都不做——见 {@link #invalidate()}）。 */
    public static void release() {
        tickHandover();
        if (holding) {
            // 交接保持期间鼠标很可能已经移向工作区：不撤幽灵、不还原（放置流程是主人）
            hoverOwner = null;
            shown = null;
            return;
        }
        hoverOwner = null;
        shown = null;
        endPreview();
    }

    /**
     * 点击放置可合成配方 → **不要立刻撤下幽灵**：真实物品由服务端放置，客户端要等回包，
     * 这中间撤掉幽灵会露出一段空工作区（用户 2026-09-27 反馈）。进入"交接保持"：
     * 幽灵留在画面上、真实物品继续暂隐，直到 {@link #tickHandover} 判出物品到位或超时。
     *
     * @param display 被点击的配方（{@code null} = 判不出来，只能按当前预览的配方处理）
     * @return true = 已进入交接保持（调用方需要在原版清空幽灵后调用
     *         {@link #reinstallHeldGhost()} 把幽灵写回来）
     */
    public static boolean beginPlacementHandover(@Nullable RecipeDisplay display) {
        if (previewing && shown != null && (display == null || display == shown)) {
            holding = true;
            heldSince = System.currentTimeMillis();
            heldDisplay = shown;
            // 幽灵所有权在放置流程手里：不还原旧快照（原版随后会清空并自己写它的幽灵）
            overridden = true;
            snapshot = null;
            handedOver = true;
            handedOverDisplay = shown;
            return true;
        }
        invalidate();
        return false;
    }

    /**
     * 原版 {@code tryPlaceRecipe} 会先 {@code ghostSlots.clear()} 再发包——交接保持期间要把
     * 幽灵写回去，否则"保持"保的是一片空白（这正是用户看到空窗期的直接原因）。
     */
    public static void reinstallHeldGhost() {
        if (!holding || activeBook == null || heldDisplay == null) {
            return;
        }
        GhostSlots slots = ghostSlots(activeBook);
        if (slots == null) {
            return;
        }
        if (!((GhostSlotsAccessor) slots).getIngredients().isEmpty()) {
            return; // 原版/服务端已经写了幽灵（材料不齐的回包），别覆盖
        }
        install(activeBook, heldDisplay);
    }

    /** 交接保持是否正在进行（自研书的界面 mixin 用它挡掉"槽位一变就结束引导"）。 */
    public static boolean isHoldingHandover() {
        return holding;
    }

    /** 强制结束交接保持（配方书收起等）：清掉幽灵、交还真实物品的显示权。 */
    public static void cancelHandover() {
        endHold();
    }

    /** 交接保持的逐帧推进：真实物品到位 / 超时 / 换界面 → 结束。 */
    private static void tickHandover() {
        if (!holding) {
            return;
        }
        RecipeBookComponent<?> book = activeBook;
        GhostSlots slots = book == null ? null : ghostSlots(book);
        if (book == null || book != currentBook() || slots == null
                || workspaceHasRecipe(slots)
                || System.currentTimeMillis() - heldSince > HOLD_TTL_MS) {
            endHold();
        }
    }

    /** 结束交接保持：停止暂隐真实物品，并清掉我们写进去的那份幽灵（实物/原版幽灵自己会画）。 */
    private static void endHold() {
        if (!holding) {
            // 没有交接保持 → 什么都不动（保持 invalidate() 原有的"只交还所有权、不碰幽灵"语义：
            // 原版流程随后自己会 clear + 写它的幽灵）
            return;
        }
        RecipeBookComponent<?> book = activeBook;
        holding = false;
        heldDisplay = null;
        previewing = false;
        activeBook = null;
        snapshot = null;
        if (book != null) {
            GhostSlots slots = ghostSlots(book);
            if (slots != null) {
                slots.clear();
            }
        }
    }

    /**
     * 原版流程（点击放置 / 服务端回包）接管了幽灵槽位：**预览当场结束**，放弃还原权与所有权，
     * 并把本悬停目标标记为"已交给原版"（{@link #isHandedOver()}）——鼠标不动也不再重新预览。
     *
     * <p>用户 2026-09-27 实测缺陷：点击可合成配方后真实物品**当场被藏起来**（工作区看起来空的），
     * 鼠标拿开再移回才恢复。成因两段（26.3 字节码核实）：① 客户端 {@code tryPlaceRecipe} 先
     * {@code ghostSlots.clear()} 再发放置包；② 服务端只在
     * {@code PostPlaceAction.PLACE_GHOST_RECIPE}（材料不齐）时才回
     * {@code ClientboundPlaceGhostRecipePacket}——材料齐全时**没有幽灵回包**。于是"幽灵空 +
     * 悬停态仍算 previewing" ⇒ {@code isPreviewing()} 恒真 ⇒ 摆好的真实物品全被
     * {@code hoverghost/AbstractContainerScreenSlotMixin} 藏掉。此前这里只置 {@code overridden}
     * （管的是"别用旧快照覆盖"），没有结束预览本身，而 {@link #hover} 的身份短路又让下一帧
     * 直接 return、永远不重判。</p>
     */
    public static void invalidate() {
        boolean wasPreviewing = previewing || holding;
        // 交接保持一并结束（幽灵清掉；随后原版流程会写它自己的那份）
        endHold();
        if (wasPreviewing) {
            overridden = true;
        }
        // 预览当场结束：不再藏真实物品（幽灵的所有权已经在原版手里，我们什么都不还原）
        previewing = false;
        activeBook = null;
        snapshot = null;
        // 所有权变了（点击放置 / 服务端回包）→ 抑制状态同样作废，悬停目标变化后用新的工作区内容重判
        suppressed = false;
        // 本悬停目标交给原版：直到悬停目标变化，不再预览（否则下一帧身份短路失效 → 又把刚放好的
        // 材料重新藏起来盖上一层我们自己写的幽灵）。记**配方身份**，页面重建按钮也不受影响。
        handedOver = true;
        handedOverDisplay = shown;
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
        handedOverDisplay = null;
    }

    /** 悬停目标变化 / 释放时清掉交接标记（{@link #endPreview()} 也会清）。 */
    public static void clearHandedOver() {
        handedOver = false;
        handedOverDisplay = null;
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
        suppressed = false;
        handedOver = false;
        handedOverDisplay = null;
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

    /**
     * 工作区**已经摆好这条配方**——幽灵的每个**材料**条目在对应槽位里都已经是候选物品之一
     * （用户 2026-09-27 收尾诉求）。
     *
     * <p>判据直接取自**原版刚写好的那份幽灵**（{@link RecipeBookComponent#fillGhostRecipe}）：
     * 槽位映射由原版产出（造型走 {@code PlaceRecipeHelper}、无序按序对齐、熔炉给输入+燃料…），
     * 所以不必按配方类型各写一遍，也不会随版本漂移。</p>
     *
     * <p><b>结果槽条目不参与判定</b>：熔炉的结果槽要等烧炼完成才是满的，算进去会让"输入+燃料
     * 已摆好"的熔炉永远判不出"已摆好"。全员都是结果槽（没有材料条目）也不视为已摆好。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean workspaceHasRecipe(GhostSlots slots) {
        Reference2ObjectMap<Slot, ?> map = ((GhostSlotsAccessor) slots).getIngredients();
        boolean anyIngredient = false;
        for (Reference2ObjectMap.Entry<Slot, ?> entry : map.reference2ObjectEntrySet()) {
            GhostSlotAccessor ghost = (GhostSlotAccessor) entry.getValue();
            if (ghost.brbe$isResultSlot()) {
                continue;
            }
            anyIngredient = true;
            ItemStack real = entry.getKey().getItem();
            if (real.isEmpty()) {
                return false;
            }
            boolean matched = false;
            for (ItemStack candidate : ghost.brbe$getItems()) {
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
