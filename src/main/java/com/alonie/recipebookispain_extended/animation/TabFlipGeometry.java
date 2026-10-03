package com.alonie.recipebookispain_extended.animation;

import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;

/**
 * RBIP 标签栏翻页动画的几何：缩进位移 + 裁剪框。
 *
 * <p><b>动画形状</b>：退场标签从静止位朝书体中心平移（左列 +X、上排 +Y、下排 -Y），
 * 进场标签从书体内部沿同一条轴线反向滑出 —— 两者都只在单轴上移动。位移量
 * {@link #RETRACT_DISTANCE} 取标签的长边（35），足以让标签整块越过书体边缘，
 * 所以 {@code extend = 0} 即「完全被配方书盖住」。</p>
 *
 * <p><b>裁剪框 = 静止位矩形，但书体那一侧收到书体真实边缘</b>：配方书必须**始终盖在
 * 移动的标签上面**（用户 2026-10-03 追加的要求），所以标签越过书体边缘的部分一律不画
 * —— 视觉上就是「滑进书皮底下」。静止位矩形在书体那一侧本来还要多出 5px
 * （左列标签 x = 书体左缘 - 30、宽 35），这 5 源列在**未选中**贴图里是全透明的
 * （`recipe_book/tab.png` 第 30–34 列 alpha=0，BRBE 的 `rbip/{top,bottom}_tab.png` 同样），
 * 而动画期间所有参与标签都按未选中态绘制（见「选中态挂起」），所以收到书体边缘
 * **一个像素都不少**：静止帧与原版逐像素一致，动画起止都不会跳变。</p>
 *
 * <p>静止位矩形另外三侧还要向外放 {@link #CLIP_PADDING} px：固定住的标签标记（pin）图形
 * 悬在标签左上角外面，裁掉它会让动画期间 pin 缺角、动画结束再跳回来。</p>
 */
public final class TabFlipGeometry {

    public static final int TAB_WIDTH = 35;
    public static final int TAB_HEIGHT = 27;
    public static final int ROTATED_TAB_WIDTH = 27;
    public static final int ROTATED_TAB_HEIGHT = 35;

    /** 缩进位移：{@link #OUTSIDE_EXTENT} 时标签已整块没入书体，多走的 5px 只是留余量。 */
    public static final int RETRACT_DISTANCE = 35;

    /** 静止位矩形「书体那一侧」之外的三侧放行量（pin 图标悬出标签边缘）。 */
    public static final int CLIP_PADDING = 5;

    /** 标签伸出书体之外的宽度：三种朝向都是 30（左列 x = 书体左缘 - 30、上/下排同理）。 */
    public static final int OUTSIDE_EXTENT = 30;

    /**
     * 书体边缘相对标签静止位的偏移：
     *
     * <ul>
     *   <li>左列 / 上排：标签朝左 / 朝上伸出，书体在 {@link #OUTSIDE_EXTENT} px 处；</li>
     *   <li>下排：标签朝下伸出，静止位顶端就是重叠段的上沿，书体下缘在
     *       {@code 高度 - 伸出量} = 5px 处。</li>
     * </ul>
     *
     * ⚠️ 三个数字必须与 {@code RecipeBookWidgetMixin} 的布局常量一致
     * （{@code rbip$getTabX() = 书体左缘 - 30}、{@code rbip$getTopTabY() = 书体上缘 - 30}、
     * {@code rbip$getBottomTabY() = 书体下缘 - 5}）——改布局时一起改。
     */
    public static int bookEdgeOffset(RecipeGroupButtonPlacement placement) {
        return placement == RecipeGroupButtonPlacement.BOTTOM
                ? ROTATED_TAB_HEIGHT - OUTSIDE_EXTENT
                : OUTSIDE_EXTENT;
    }

    private TabFlipGeometry() {
    }

    public static int width(RecipeGroupButtonPlacement placement) {
        return placement == RecipeGroupButtonPlacement.NORMAL ? TAB_WIDTH : ROTATED_TAB_WIDTH;
    }

    public static int height(RecipeGroupButtonPlacement placement) {
        return placement == RecipeGroupButtonPlacement.NORMAL ? TAB_HEIGHT : ROTATED_TAB_HEIGHT;
    }

    /** 当前缩进位移（0 = 静止位，{@link #RETRACT_DISTANCE} = 完全没入书体）。 */
    public static int shift(TabFlipState state) {
        return Math.round((1.0F - state.extend) * RETRACT_DISTANCE);
    }

    /** 动画期间标签的实际绘制 x（只走单轴：左列沿 X）。 */
    public static int renderX(TabFlipState state) {
        return state.placement == RecipeGroupButtonPlacement.NORMAL
                ? state.baseX + shift(state)
                : state.baseX;
    }

    /** 动画期间标签的实际绘制 y（只走单轴：上排 +Y、下排 -Y）。 */
    public static int renderY(TabFlipState state) {
        return switch (state.placement) {
            case TOP -> state.baseY + shift(state);
            case BOTTOM -> state.baseY - shift(state);
            case NORMAL -> state.baseY;
        };
    }

    /**
     * 写入裁剪框（{@code out = {x0, y0, x1, y1}}）：静止位矩形向外放
     * {@link #CLIP_PADDING} px，但<b>书体那一侧收在书体边缘</b>
     * （{@code base + bookEdgeOffset(placement)}）—— 配方书永远盖在标签上面。
     *
     * <p>翻页动画与选中态渐变共用这一条：前者裁的是移动中的标签，后者裁的是
     * 「还没完全选中」的标签（只有完全选中时才允许像原版那样压在书皮上）。</p>
     */
    public static void clip(int baseX, int baseY, RecipeGroupButtonPlacement placement, int[] out) {
        int w = width(placement);
        int h = height(placement);
        int pad = CLIP_PADDING;
        int base = placement == RecipeGroupButtonPlacement.NORMAL ? baseX : baseY;
        int edge = base + bookEdgeOffset(placement);
        switch (placement) {
            case NORMAL -> {
                // 右边界 = 书体左缘（标签越过书皮的像素一律不画）
                out[0] = baseX - pad;
                out[1] = baseY - pad;
                out[2] = edge;
                out[3] = baseY + h + pad;
            }
            case TOP -> {
                // 下边界 = 书体上缘
                out[0] = baseX - pad;
                out[1] = baseY - pad;
                out[2] = baseX + w + pad;
                out[3] = edge;
            }
            case BOTTOM -> {
                // 上边界 = 书体下缘
                out[0] = baseX - pad;
                out[1] = edge;
                out[2] = baseX + w + pad;
                out[3] = baseY + h + pad;
            }
        }
    }
}
