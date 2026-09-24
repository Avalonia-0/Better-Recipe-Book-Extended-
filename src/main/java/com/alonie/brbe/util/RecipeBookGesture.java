package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.CompatSelfCheck;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * 配方书滚轮的<b>唯一归属判定</b>与<b>唯一消费点</b>（2026-09-25 接缝收敛）。
 *
 * <h3>为什么要收敛</h3>
 * 这个手势此前有三套各自为政的实现在互相踩：
 * <ol>
 *   <li>BRBE {@code mixins/MouseScrollHandler}（RETURN 注入）只负责把滚动排进
 *       {@link BetterRecipeBook#queuedScroll}；</li>
 *   <li>RBIP {@code mixin/MouseMixin}（HEAD 注入）负责标签栏翻页并 {@code ci.cancel()}；</li>
 *   <li>mousewheelie 兼容 mixin 事后补救——而 26.x 上它落在一条<b>没有任何实现类</b>的死分支
 *       （{@code IScrollableRecipeBook}），等于完全没生效。</li>
 * </ol>
 * 副作用是真实发生过的：mousewheelie 自己的配方书滚轮实现（走 {@code ISpecialScrollableScreen}）
 * 把配方页瞬翻（没有 BRBE 的滑动动画与音效），在书左侧标签条滚动还会直接切换选中的标签；
 * 它的滚轮键位是 amecs <b>priority</b> 键位，命中时 amecs 会把整个
 * {@code MouseHandler.onScroll} 取消掉——BRBE 的 RETURN 注入连事件都收不到。
 *
 * <h3>现在的结构</h3>
 * 判定与消费都只在这里：{@link #claimScroll} 在 {@code MouseHandler.onScroll} 的 <b>HEAD</b>
 * 由 BRBE（{@code MouseScrollHandler}）与 RBIP（{@code MouseMixin}）调用；先跑到的那一个认领并
 * {@code ci.cancel()}，另一个连同原版方法体一起被跳过，所以<b>注入顺序不再影响结果</b>；
 * 下游（mousewheelie / amecs priority 键位 / 原版快捷栏滚动）根本看不到这次事件。
 * 认领成功后滚动照旧进 {@link BetterRecipeBook#queuedScroll}，由
 * {@code scrollablepages/RecipeBookPageMixin} 在渲染时翻页——动画、音效、残缺红罩、
 * pin 图标全部沿用既有链路，一行都不用改。
 *
 * <p><b>范围</b>：只认领<b>原版配方书界面</b>（{@code AbstractRecipeBookScreen}：背包/工作台/
 * 熔炉族）。BRBE 自研的酿造台/锻造台书是另一套界面，没有第二个模组来抢，走
 * {@code MouseScrollHandler} 的兜底入队路径（与其页面自己的命中判定配套），不在本类干预。</p>
 */
public final class RecipeBookGesture {

    /** 原版配方书面板尺寸（与 RBIP / mousewheelie 使用同一组常量）。 */
    public static final int BOOK_WIDTH = 147;
    public static final int BOOK_HEIGHT = 166;
    /** 书体左侧的标签条宽度（RBIP 标签列 / mousewheelie 的"滚轮切标签"区）。 */
    public static final int TAB_STRIP_WIDTH = 30;

    private RecipeBookGesture() {}

    /**
     * 唯一入口：这次滚动是否归配方书？归的话顺带排进
     * {@link BetterRecipeBook#queuedScroll}。
     *
     * @return {@code true} = 已认领，调用方<b>必须</b> {@code ci.cancel()}：原版方法体
     *         （{@code Screen.mouseScrolled}、快捷栏滚动）与其它模组的滚轮实现都不该再看到它
     */
    public static boolean claimScroll(double mouseX, double mouseY, double verticalAmount) {
        if (verticalAmount == 0.0D) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.getOverlay() != null) {
            return false;
        }
        // ★ 桌面窗口语义优先（2026-09-25 修正）：查询界面 / pin / 预览 拥有光标时，
        // 滚轮归它们 —— 必须在这里把事件转交给 RecipeViewerOverlay 自己的分发器，
        // 而不是像以前那样等屏幕分发。因为接缝一旦按"配方书矩形"认领就会 cancel：
        //   ① 窗口的滚轮分发器再也收不到 → 窗口翻不了页；
        //   ② 配方书自己又会因为"窗口压在上面"（RecipeBookPageMixin 的
        //      modalMaskOwnsCursor 守卫）把入队的滚动丢掉 → 配方书也不翻页。
        // 认领并且不再往下走，顺带挡掉 mousewheelie / amecs priority 键位 /
        // 原版快捷栏滚动。
        if (RecipeViewerOverlay.modalMaskOwnsCursor(
                net.minecraft.util.Mth.floor(mouseX), net.minecraft.util.Mth.floor(mouseY))) {
            RecipeViewerOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }
        Screen screen = minecraft.screen;
        if (!(screen instanceof AbstractRecipeBookScreen<?> bookScreen)) {
            return false;
        }
        RecipeBookComponent<?> component = componentOf(bookScreen);
        if (component == null || !component.isVisible()) {
            return false;
        }

        // ① RBIP 标签栏（标签页翻页）：左列 / 上下条带 / 翻页箭头，与配方网格互斥。
        //    扩展功能关闭时 rbip$scrollPages 内部自己返回 false，这里无需再判开关。
        if (component instanceof RecipeBookScrollAccess tabScroll
                && tabScroll.rbip$scrollPages(mouseX, mouseY, verticalAmount)) {
            bookScreen.afterMouseAction();
            CompatSelfCheck.noteSeamClaim("RBIP 标签栏翻页");
            return true;
        }
        // ② 配方书面板本体 + 其左侧标签条 → 排队；渲染时由配方页翻页（动画 + 音效）。
        if (ownsBookPanel(component, mouseX, mouseY)) {
            BetterRecipeBook.queuedScroll = verticalAmount > 0.0D ? -1 : 1;
            CompatSelfCheck.noteSeamClaim("配方书翻页");
            return true;
        }
        return false;
    }

    /**
     * 光标是否落在原版配方书面板或其左侧标签条上（纯几何，不看事件）。
     *
     * <p>给 mousewheelie 兼容做兜底：接缝在前，正常情况根本轮不到它；万一接缝没认领
     * （比如某些版本 RBIP/BRBE 的 HEAD 注入没应用），至少让 mousewheelie 别在书区域内
     * 自作主张翻页/切标签。</p>
     */
    public static boolean ownsRecipeBookArea(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return false;
        }
        Screen screen = minecraft.screen;
        if (!(screen instanceof AbstractRecipeBookScreen<?> bookScreen)) {
            return false;
        }
        RecipeBookComponent<?> component = componentOf(bookScreen);
        return component != null && component.isVisible()
                && ownsBookPanel(component, mouseX, mouseY);
    }

    /** 书体面板 + 左侧标签条（标签条归属 BRBE/RBIP，mousewheelie 在这里的"切标签"要挡掉）。 */
    private static boolean ownsBookPanel(RecipeBookComponent<?> component, double mouseX, double mouseY) {
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) component;
        int left = accessor.brbe$invokeGetXOrigin();
        int top = accessor.brbe$invokeGetYOrigin();
        if (mouseY < top || mouseY >= top + BOOK_HEIGHT) {
            return false;
        }
        return mouseX >= left - TAB_STRIP_WIDTH && mouseX < left + BOOK_WIDTH;
    }

    private static RecipeBookComponent<?> componentOf(AbstractRecipeBookScreen<?> screen) {
        return ((AbstractRecipeBookScreenAccessor) screen).brbe$getRecipeBookComponent();
    }
}
