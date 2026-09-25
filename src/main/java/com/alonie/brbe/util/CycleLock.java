package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * 「锁定折叠物品」（配置项 {@code cycleLockKey}，默认 Alt）的**逐物品**锁定状态。
 *
 * <p>语义（用户 2026-09-13 诉求 2）：<b>只有指针下的那一个折叠物品会被冻结</b>，
 * 锁定键+滚轮也只逐格翻动它；同一界面上其余的折叠物品照常自动轮换。旧实现把
 * 「锁定键」做成界面级开关（按住就冻住整个界面的折叠物品），与用户的期望相反。</p>
 *
 * <p>工作方式：每个前端在**绘制自己负责的每一件折叠物品**时调用
 * {@link #claim(Object, int, int, int, int)}（指针落在它上面、且锁定键按住 → true，
 * 同时把它登记为本帧「指针下的物品」），命中后用 {@link #indexFor(Object, int)}
 * 取它当前应显示的下标（首次命中时把当时的自动下标 latch 下来，之后由滚轮步进
 * 改变）；没被指着的物品调用 {@link #release(Object)} → 回到自动轮换。</p>
 *
 * <p>滚轮步进（{@link #step(double)}）只作用于本帧登记的那一件物品；没有物品被
 * 指着时返回 false，调用方照旧处理滚轮（翻页等）。</p>
 *
 * <p>key 用物品自身的身份：按钮用 widget 实例、幽灵物品用 {@code Slot} 实例、
 * 预览面板里的槽位用 {@link SlotKey}（配方 id + 槽位序号，值相等）。</p>
 *
 * <h3>两层</h3>
 * <p>LEI 浮层（查询窗口 / 预览弹窗 / pin）绘制在容器界面之上：屏幕层的物品判定
 * 走 {@link #claimScreen}，指针被 LEI 浮层挡住时一律不判定（否则浮层背后那件
 * 配方书物品会抢占「指针下的物品」，滚轮就会去翻一个看不见的东西）。</p>
 *
 * <h3>配方书共享的 SlotSelectTime</h3>
 * <p>原版配方书把**同一个** {@code SlotSelectTime} 交给网格按钮与幽灵物品，没有
 * 上下文就分不清"现在画的是哪一件"。所以配方书前端在用下标之前先
 * {@link #pushContext} 压入正在绘制的物品（见 {@link CycleLockSlotSelectTime}），
 * 由 {@link #resolveContext} 在该物品上做逐物品判定。</p>
 */
public final class CycleLock {

    /** 指针下的物品（本帧绘制时登记）——滚轮步进只作用于它。 */
    private static Object hoveredKey;
    private static long hoveredAt;

    /** 已冻结物品 → 冻结下标（恒 ≥ 0；消费方用普通取模，负数会越界）。
     *  同一条目还记着「最后一次被登记为指针下物品」的时刻：解除冻结带一点宽限，
     *  这样**同一个配方同时显示在两处**（如预览弹窗与 pin / tooltip 内嵌预览）
     *  时，没被指着的那一处不会把指着的那一处的冻结状态抹掉。 */
    private static final Map<Object, Latch> LATCHED = new HashMap<>();

    private static final class Latch {
        int index;
        long claimedAt;

        Latch(int index, long claimedAt) {
            this.index = index;
            this.claimedAt = claimedAt;
        }
    }

    /** 按键状态缓存：每帧几百次判定，不必每次都去问 GLFW。 */
    private static boolean downCache;
    private static long downCacheAt;

    /** 上一次观测到的按键状态：松开时清空冻结表（下次按下从新的变体开始）。 */
    private static boolean wasDown;

    /** 绘制上下文：配方书正在绘制的折叠物品（见类注释）。 */
    private static Object ctxKey;
    private static int ctxX;
    private static int ctxY;
    private static int ctxW;
    private static int ctxH;

    /** 登记过的「指针下物品」的有效期：滚轮事件在两帧之间到达，取上一帧的
     *  登记结果；超过这个时长未刷新说明指针早就不在那件物品上了。 */
    private static final long HOVER_TTL_MS = 250L;
    /** 解除冻结的宽限：指针离开后这么久才真的放回自动轮换。同一配方同时显示
     *  在两处时，没被指着的那一处每帧都会调 {@code release}，宽限保证它不会把
     *  指着的那一处的冻结抹掉（两处的绘制顺序不保证）。 */
    private static final long RELEASE_GRACE_MS = 150L;
    /** 按键轮询缓存时长。 */
    private static final long DOWN_CACHE_MS = 5L;

    private CycleLock() {
    }

    // ── 按键状态 ────────────────────────────────────────────────────────────

    /** 锁定键是否按住（配置项「锁定折叠物品」）。松开的那一帧清空全部冻结状态。 */
    public static boolean isDown() {
        long now = System.currentTimeMillis();
        if (now - downCacheAt < DOWN_CACHE_MS) {
            return downCache;
        }
        downCacheAt = now;
        downCache = ClientCompat.isCycleLockDown();
        if (!downCache && wasDown) {
            LATCHED.clear();
            hoveredKey = null;
        }
        wasDown = downCache;
        return downCache;
    }

    /** 清掉本帧的「指针下物品」登记：**上层浮层**（LEI 预览弹窗 / pin）接管指针
     *  时先调用它，再由自己的槽位各自 {@link #claim} —— 铺在它下面的界面物品
     *  就不会抢占滚轮（否则指着弹窗空白处按锁定键+滚轮，动的会是弹窗背后那件
     *  看不见的物品）。 */
    public static void clearHovered() {
        hoveredKey = null;
    }

    // ── 逐物品判定 ──────────────────────────────────────────────────────────

    /** 指针是否落在这件物品上、且锁定键按住：是 → true 并把它登记为**本帧
     *  指针下的物品**；否则 false（调用方应随之 {@link #release} 它）。 */
    public static boolean claim(Object key, int x, int y, int w, int h) {
        if (key == null || !isDown()) return false;
        if (!cursorInside(x, y, w, h)) return false;
        long now = System.currentTimeMillis();
        hoveredKey = key;
        hoveredAt = now;
        Latch latch = LATCHED.get(key);
        if (latch != null) {
            latch.claimedAt = now;
        }
        return true;
    }

    /** 屏幕层（配方书网格按钮 / 功能方块幽灵物品）的判定：指针被 LEI 查询
     *  窗口 / 预览弹窗 / pin 挡住时不做任何判定——那些浮层在自己的层里各管
     *  各的（它们的物品会用 {@link #claim} 登记）。 */
    public static boolean claimScreen(Object key, int x, int y, int w, int h) {
        if (RecipeViewerOverlay.modalMaskOwnsCursor(cursorX(), cursorY())) return false;
        return claim(key, x, y, w, h);
    }

    /** 这件物品当前应显示的下标：首次冻结时 latch {@code autoIndex}，
     *  之后由 {@link #step} 逐格改变；指针离开又回来（超过宽限期）则按当时
     *  显示的变体重新 latch。返回值恒 ≥ 0。 */
    public static int indexFor(Object key, int autoIndex) {
        long now = System.currentTimeMillis();
        Latch latch = LATCHED.get(key);
        if (latch == null) {
            latch = new Latch(Math.max(0, autoIndex), now);
            LATCHED.put(key, latch);
        } else if (now - latch.claimedAt > RELEASE_GRACE_MS) {
            // 上一次的冻结早已过期（指针刚移回来）：按现在显示的变体重新冻结。
            latch.index = Math.max(0, autoIndex);
        }
        latch.claimedAt = now;
        return Math.max(0, latch.index);
    }

    /** 这件物品没被指着：解除冻结，回到自动轮换（不需要先判空）。宽限期内
     *  （同帧/近帧刚被别处登记过）不解除——见 {@link #RELEASE_GRACE_MS}。 */
    public static void release(Object key) {
        if (key == null) return;
        Latch latch = LATCHED.get(key);
        if (latch == null) return;
        if (System.currentTimeMillis() - latch.claimedAt > RELEASE_GRACE_MS) {
            LATCHED.remove(key);
        }
    }

    /** 该物品是否正被冻结（供渲染器决定要不要接管它的显示变体）。 */
    public static boolean isLatched(Object key) {
        return key != null && LATCHED.containsKey(key);
    }

    /** 指针下那件物品当前显示的下标，否则 {@code autoIndex}（tooltip 之类的
     *  查询用：指针下的物品必然是用户看到的那一件）。 */
    public static int hoveredOr(int autoIndex) {
        Object key = hovered();
        Latch latch = key == null ? null : LATCHED.get(key);
        return latch == null ? autoIndex : Math.max(0, latch.index);
    }

    // ── 滚轮 ────────────────────────────────────────────────────────────────

    /** 锁定键+滚轮：逐格翻动**指针下**那一件折叠物品。
     *
     *  @param vertical 滚轮增量（与原版一致：&gt; 0 = 上滚 = 上一个变体）
     *  @return true = 有物品被翻动（调用方应吞掉这次滚轮，不翻页） */
    public static boolean step(double vertical) {
        if (vertical == 0) return false;
        return step(vertical > 0 ? -1 : 1);
    }

    /** 同上，直接给格数（{@code -1} = 上一个变体）。 */
    public static boolean step(int delta) {
        if (delta == 0 || !isDown()) return false;
        Object key = hovered();
        if (key == null) return false;
        Latch latch = LATCHED.get(key);
        if (latch == null) return false; // 需要先有一帧绘制把它 latch 下来
        latch.index = Math.max(0, latch.index + delta);
        // 这次步进就是最新的意图：别让紧随其后的绘制把它当成过期冻结重量。
        latch.claimedAt = System.currentTimeMillis();
        return true;
    }

    /**
     * 消费**排队中的那次滚轮**（{@code BetterRecipeBook.queuedScroll}）：锁定键按住、
     * 指针下有一件折叠物品时把它逐格翻动，翻到了就清空队列并返回 {@code true}。
     *
     * <p>为什么要单独有这样一个入口：配方书页只在**书体可见**时绘制，而幽灵物品在
     * 书体收起后依然显示——原版点一下配方就会 {@code setVisible(false)}，工作台上
     * 「幽灵物品留在合成格里」正是书体收起的状态。于是"指针停在幽灵物品上按锁定键+
     * 滚轮"在书体收起时没有任何人消费队列（用户 2026-09-26 反馈：能锁定、滚轮翻不动）。
     * 修复：**幽灵物品自己的绘制路径**（每帧都跑）也调用这里，与配方书页的同类分支
     * 共用同一份判定——谁先跑到谁消费，队列清空后另一个自然不再重复步进。</p>
     */
    public static boolean consumeQueuedScroll() {
        int queued = BetterRecipeBook.queuedScroll;
        if (queued == 0 || !isDown()) return false;
        // 与 claimScreen 同口径：指针被 LEI 查询窗口/pin/预览挡住时不插手——那些浮层
        // 的折叠槽位由它们自己的滚轮分发器步进（它们不往 queuedScroll 里排队）。
        if (RecipeViewerOverlay.modalMaskOwnsCursor(cursorX(), cursorY())) return false;
        if (!step(queued)) return false;
        BetterRecipeBook.queuedScroll = 0;
        return true;
    }

    // ── 绘制上下文（配方书共享的 SlotSelectTime）────────────────────────────

    /** 压入「正在绘制的折叠物品」（屏幕坐标矩形），供共享的
     *  {@code SlotSelectTime} 做逐物品判定。 */
    public static void pushContext(Object key, int x, int y, int w, int h) {
        ctxKey = key;
        ctxX = x;
        ctxY = y;
        ctxW = w;
        ctxH = h;
    }

    /** 弹出上下文（每次 push 配一次 pop；不在上下文里时无副作用）。 */
    public static void popContext() {
        ctxKey = null;
    }

    /** 共享 SlotSelectTime 的取值入口：有上下文 → 该物品的逐物品判定结果，
     *  否则原样透传自动下标。 */
    public static int resolveContext(int autoIndex) {
        Object key = ctxKey;
        if (key == null) return autoIndex;
        if (claimScreen(key, ctxX, ctxY, ctxW, ctxH)) {
            return indexFor(key, autoIndex);
        }
        release(key);
        return autoIndex;
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    /** 本帧登记为「指针下的物品」的那一件（过期即视为没有）。 */
    public static Object hovered() {
        if (hoveredKey == null) return null;
        return System.currentTimeMillis() - hoveredAt <= HOVER_TTL_MS ? hoveredKey : null;
    }

    public static int cursorX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) return Integer.MIN_VALUE;
        return (int) Math.floor(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    public static int cursorY() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) return Integer.MIN_VALUE;
        return (int) Math.floor(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    private static boolean cursorInside(int x, int y, int w, int h) {
        int mx = cursorX();
        int my = cursorY();
        if (mx == Integer.MIN_VALUE) return false;
        return mx >= x && mx < x + Math.max(1, w) && my >= y && my < y + Math.max(1, h);
    }

    /** 预览面板 / pin / 按钮里的槽位 key：同一件折叠物品在**同一配方**里的
     *  同一个槽位。值相等（每次绘制新建实例也没问题）。 */
    public static Object slotKey(net.minecraft.world.item.crafting.display.RecipeDisplayId id, int slot) {
        return new SlotKey(id, slot);
    }

    /** {@link #slotKey} 的 key 类型。 */
    public record SlotKey(net.minecraft.world.item.crafting.display.RecipeDisplayId recipe, int slot) {
    }
}
