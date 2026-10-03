package com.alonie.brbe.util;

import net.minecraft.util.Mth;

public final class AlternativeOverlayLayout {
    private static final int DEFAULT_SMALL_COLUMNS = 4;
    private static final int DEFAULT_LARGE_COLUMNS = 5;
    private static final int SMALL_LAYOUT_LIMIT = 16;
    private static final int MAX_ROWS_BEFORE_EXPANDING = 5;

    /** 浮层盒与屏幕边缘的最小间距（放得下时）。 */
    public static final int SCREEN_MARGIN = 30;

    /** 配方格步距（与原版 {@code RecipeButton} 同宽）。 */
    public static final int CELL = 25;

    /** 原版配方书面板尺寸（定位公式里的"页面区域中心"由它推出）。 */
    public static final int BOOK_WIDTH = 147;
    public static final int BOOK_HEIGHT = 166;

    private AlternativeOverlayLayout() {
    }

    public static int columnsFor(int recipeCount) {
        if (recipeCount <= 0) {
            return DEFAULT_SMALL_COLUMNS;
        }

        int columns = recipeCount <= SMALL_LAYOUT_LIMIT ? DEFAULT_SMALL_COLUMNS : DEFAULT_LARGE_COLUMNS;
        if (Mth.ceil((float) recipeCount / (float) columns) <= MAX_ROWS_BEFORE_EXPANDING) {
            return columns;
        }

        return Mth.ceil((float) recipeCount / (float) MAX_ROWS_BEFORE_EXPANDING);
    }

    /**
     * 把浮层盒夹进屏幕：放得下时四周留 {@link #SCREEN_MARGIN}px，放不下时贴边。
     *
     * <p>配方书替代配方组（原版 + BRBE 自研书）与查询窗口共用这一条规则；收口在这里是为了
     * 让"分页后按本页几何重算坐标"的那条路径（{@code OverlayRecipeComponentPagingMixin}）
     * 与原版路径得到**同一个**结果——否则两条路径的夹取规则一旦漂移，
     * 分页盒又会跑到与普通盒不一样的地方。</p>
     *
     * @return {@code {x, y}}
     */
    public static int[] clampToScreen(int x, int y, int boxW, int boxH, int screenW, int screenH) {
        int boxX = boxW <= screenW - 2 * SCREEN_MARGIN
                ? Math.max(SCREEN_MARGIN, Math.min(x, screenW - boxW - SCREEN_MARGIN))
                : Math.max(0, Math.min(x, screenW - boxW));
        int boxY = boxH <= screenH - 2 * SCREEN_MARGIN
                ? Math.max(SCREEN_MARGIN, Math.min(y, screenH - boxH - SCREEN_MARGIN))
                : Math.max(0, Math.min(y, screenH - boxH));
        return new int[]{boxX, boxY};
    }

    /**
     * 替代配方组浮层的**定位**（原版规则，26.3 {@code OverlayRecipeComponent.init} 字节码逐式照搬）：
     * <ol>
     *   <li>盒右缘越过"页面中心 + 50" → 向左按格对齐（原版这一步用**截断**取整，不是 ceil）</li>
     *   <li>盒底越过"页面中心 + 50" → 向上按格对齐（= 把盒子留在配方格内那一步）</li>
     *   <li>盒顶高于"页面中心 - 100" → 向下贴回</li>
     * </ol>
     * 最后夹进屏幕（{@link #clampToScreen}）。
     *
     * <p>原版合成书（分页后按"本页"几何）、BRBE 自研书（锻造台）都走这里——"浮层像普通组一样
     * 贴着被点的组按钮落点"这条语义因此只有一个实现，两处不会再各自漂移。</p>
     *
     * @param anchorX 被点击的组按钮 x（原版传进 {@code init} 的第一个坐标就是它）
     * @param anchorY 被点击的组按钮 y
     * @param items   本次要摆的条目数（分页时 = 本页条目数）
     * @param columns 本次的列数
     * @param boxW    盒子宽（夹取用）
     * @param boxH    盒子高（夹取用）
     * @param centerX 配方页面区域中心的 x（{@link #pageCenterX}）
     * @param centerY 配方页面区域中心的 y（{@link #pageCenterY}）
     * @param cell    格宽（原版 = 按钮宽度 25）
     * @return {@code {x, y}}
     */
    public static int[] placeBox(int anchorX, int anchorY, int items, int columns, int boxW, int boxH,
                                 int centerX, int centerY, float cell, int screenW, int screenH) {
        int boxX = anchorX;
        float right = boxX + Math.min(items, columns) * (float) CELL;
        float centerRight = centerX + 50.0F;
        if (right > centerRight) {
            boxX -= (int) (cell * (float) ((int) ((right - centerRight) / cell)));
        }
        int boxY = anchorY;
        float bottom = boxY + Mth.ceil((float) items / (float) Math.max(1, columns)) * (float) CELL;
        float centerBottom = centerY + 50.0F;
        if (bottom > centerBottom) {
            boxY -= (int) (cell * (float) Mth.ceil((bottom - centerBottom) / cell));
        }
        float top = boxY;
        float centerTop = centerY - 100.0F;
        if (top < centerTop) {
            boxY -= (int) (cell * (float) Mth.ceil((top - centerTop) / cell));
        }
        return clampToScreen(boxX, boxY, boxW, boxH, screenW, screenH);
    }

    /**
     * 把浮层盒**夹进配方书面板**：{@link #placeBox} 与 {@link #clampToScreen} 都只保证不越出
     * **屏幕**，而配方书（原版合成书 + BRBE 自研书）的组浮层应该留在**书体**以内。
     *
     * <p>用户 2026-09-26 反馈：酿造台的替代配方组浮层会超出配方书界面（合成书/锻造台的都不会）。
     * 这里按面板矩形再夹一次；盒子比面板还大（极端条目数）时贴面板左上角，宁可压住配方格也不出书。</p>
     *
     * @return {@code {x, y}}
     */
    public static int[] clampToBook(int x, int y, int boxW, int boxH, int panelLeft, int panelTop) {
        int minX = panelLeft + 2;
        int maxX = panelLeft + BOOK_WIDTH - boxW - 2;
        int minY = panelTop + 2;
        int maxY = panelTop + BOOK_HEIGHT - boxH - 2;
        return new int[]{
                maxX >= minX ? Mth.clamp(x, minX, maxX) : minX,
                maxY >= minY ? Mth.clamp(y, minY, maxY) : minY};
    }

    /**
     * 配方页面区域中心的 x，相对面板原点——原版 {@code RecipeBookPage.mouseClicked} 传给浮层
     * {@code init} 的 {@code mouseX} 实参（{@code areaLeft + areaWidth/2}，即面板 + 147/2）。
     */
    public static int pageCenterX(int panelLeft) {
        return panelLeft + BOOK_WIDTH / 2;
    }

    /** 配方页面区域中心的 y：原版 = {@code areaTop + 13 + areaHeight/2}（面板 + 13 + 166/2）。 */
    public static int pageCenterY(int panelTop) {
        return panelTop + 13 + BOOK_HEIGHT / 2;
    }
}
