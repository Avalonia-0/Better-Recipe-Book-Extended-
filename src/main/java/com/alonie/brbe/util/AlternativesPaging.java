package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 替代配方组浮层的**独立滚轮翻页区**（用户 2026-09-26 诉求）：组浮层展开后盖在配方书之上，
 * 指针落在浮层区域内滚动时翻**浮层自己的页**，配方书不跟着翻。
 *
 * <p>判定挂在 {@link RecipeBookGesture#claimScroll} 的**最前面**——那里是配方书滚轮的唯一接缝
 * （BRBE 的 {@code MouseScrollHandler} 与 RBIP 的 {@code MouseMixin} 都调它），先于书体认领，
 * 所以"覆盖在配方书之上"是结构保证，而不是靠两边各自小心。BRBE 自研书（酿造台/锻造台）不是
 * {@code AbstractRecipeBookScreen}，因此这一判定必须排在那个分支之前。</p>
 *
 * <h3>★ 登记必须是"活的"（2026-09-26 用户实测 bug 修复）</h3>
 * 第一版是"渲染时登记一次、之后一直算数"，被用户抓到一个很隐蔽的 bug：
 * <b>在工作台配方书里滚轮翻页，能听见翻页音效，配方页却原地不动</b>——某个**已经不在屏幕上**的
 * 组浮层仍然占着滚轮区。
 *
 * <p>为什么第一版会这样（两处都在源码里可查）：</p>
 * <ol>
 *   <li>{@code Target.screen()} 当时由实现方**动态**返回 {@code Minecraft.gui.screen()}，
 *       于是"条目带所属界面"这条设计根本没生效——旧条目在**任何**界面都报告"我属于当前界面"；
 *       {@code track()} 里按界面清旧条目的那行也永远清不掉它。</li>
 *   <li>浮层自己的可见标志在**离开界面时无人复位**：锻造台浮层
 *       （{@code SmithingOverlayRecipeComponent.visible}）只在"点到组外"时被置 false，
 *       关掉锻造台界面/关掉配方书都不清；原版 {@code OverlayRecipeComponent.isVisible} 同理
 *       （只有 {@code RecipeBookComponent.setVisible(false)} → {@code RecipeBookPage.setInvisible()}
 *       这一条路径会清）。而这两种浮层的盒坐标与配方书配方格**几乎完全重合**（两者面板原点都是
 *       {@code (width-147)/2 - xOffset}，锻造台浮层 = 面板 +7,+26，配方格 = 面板 +11,+31），
 *       于是"配方区的滚动区域被某个看不见的东西占用了"。</li>
 * </ol>
 *
 * <p>所以现在有**两道**独立守卫，都在本类里收口：</p>
 * <ul>
 *   <li><b>界面身份</b>：界面在 {@link #track} 时**取值快照**，不再由目标动态回答；换界面即失效。</li>
 *   <li><b>心跳</b>：目标只在**真的在画**的时候才登记（两边都是渲染路径里调 {@code track}），
 *       心跳超过 {@link #LIVE_TTL_MS} 没续 = 不再画了 → 条目失效。这一条同时覆盖"同一界面内
 *       浮层其实已不可见/已不再渲染"的情形，与 {@link HoverGhostRecipe} 的预览心跳同一套写法。</li>
 * </ul>
 *
 * <p>认领仍然只认一个：从最新登记的往下找，第一个"活着 + 分页 + 指针在区内"的翻页并认领。</p>
 */
public final class AlternativesPaging {

    /** 一个可滚轮翻页的替代配方组浮层（两处实现：原版 {@code OverlayRecipeComponent} 与锻造台自研组件）。 */
    public interface Target {
        /** 是否可见且分页（≥2 页）——不满足时滚轮区不存在。 */
        boolean paged();

        /** 指针是否落在滚轮区（浮层盒 + 悬浮的翻页键）。 */
        boolean inScrollRegion(int mouseX, int mouseY);

        /** 翻一页（{@code verticalAmount > 0} = 上滚 = 上一页），含翻页音效。 */
        void flipPage(double verticalAmount);
    }

    /** 心跳有效期（毫秒）：渲染是逐帧的，滚轮事件在两帧之间到达，250ms 足够宽裕。 */
    private static final long LIVE_TTL_MS = 250L;

    /** 一条登记：目标 + **登记时**所在界面 + 最后一次心跳。 */
    private static final class Entry {
        final Target target;
        Screen screen;
        long heartbeat;

        Entry(Target target, Screen screen, long heartbeat) {
            this.target = target;
            this.screen = screen;
            this.heartbeat = heartbeat;
        }
    }

    private static final List<Entry> TRACKED = new ArrayList<>();

    private AlternativesPaging() {
    }

    /** 浮层每次渲染时登记/续心跳自己（顺手清掉换界面的与心跳过期的旧条目）。 */
    public static void track(Target target) {
        if (target == null) {
            return;
        }
        Screen screen = currentScreen();
        if (screen == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Entry found = null;
        for (int i = TRACKED.size() - 1; i >= 0; i--) {
            Entry entry = TRACKED.get(i);
            if (entry.target == target) {
                found = entry;
                continue; // 自己不能当"旧条目"清掉
            }
            if (entry.screen != screen || now - entry.heartbeat > LIVE_TTL_MS) {
                TRACKED.remove(i);
            }
        }
        if (found == null) {
            TRACKED.add(new Entry(target, screen, now));
        } else {
            // 界面快照跟着刷新：同一实例被复用到别的界面时不会带走旧身份
            found.screen = screen;
            found.heartbeat = now;
        }
    }

    /** 这次滚动是否被某个组浮层认领（认领方直接翻页）。 */
    public static boolean scroll(double mouseX, double mouseY, double verticalAmount) {
        if (verticalAmount == 0.0D || TRACKED.isEmpty()) {
            return false;
        }
        Screen screen = currentScreen();
        if (screen == null) {
            return false;
        }
        int mx = Mth.floor(mouseX);
        int my = Mth.floor(mouseY);
        long now = System.currentTimeMillis();
        boolean claimed = false;
        for (int i = TRACKED.size() - 1; i >= 0; i--) {
            Entry entry = TRACKED.get(i);
            // 换界面 / 心跳过期 / 已不再分页 → 条目作废（真活着的话下一帧会重新登记）
            if (entry.screen != screen || now - entry.heartbeat > LIVE_TTL_MS || !entry.target.paged()) {
                TRACKED.remove(i);
                continue;
            }
            if (claimed || !entry.target.inScrollRegion(mx, my)) {
                continue;
            }
            entry.target.flipPage(verticalAmount);
            claimed = true;
        }
        return claimed;
    }

    /** 当前界面（渲染与滚轮两条路径共用同一取值口径）。 */
    private static Screen currentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return null;
        }
        return minecraft.gui.screen();
    }

    /** 滚轮翻页的页码步进（上滚 = 上一页，与配方书一致）：返回新页码（已钳制，不环绕）。 */
    public static int stepPage(int page, int pageCount, double verticalAmount) {
        int next = page + (verticalAmount > 0.0D ? -1 : 1);
        return Math.max(0, Math.min(pageCount - 1, next));
    }
}
