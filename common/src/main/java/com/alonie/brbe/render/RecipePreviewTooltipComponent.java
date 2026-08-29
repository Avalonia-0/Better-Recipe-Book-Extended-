package com.alonie.brbe.render;

import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.joml.Matrix4f;

/**
 * tooltip 内嵌完整预览（1.21.11 RecipePreviewTooltipComponent 的 1.21.1 版）。
 *
 * <p>尺寸：JEI 条目 = layout*0.6 + 2*PADDING（4px）；vanilla = 48x48
 * （24px 按钮 x 2x）。{@code renderImage} 把预览画进 tooltip 行（左对齐，
 * 垂直以行顶为顶——1.21.1 的 renderImage 无 width/height 参数，行起点即
 * {@code (x, y)}）。</p>
 */
public final class RecipePreviewTooltipComponent implements ClientTooltipComponent {

    private static final float TOOLTIP_SCALE = 0.6f;
    private static final int PADDING = 4;

    private final RecipeHolder<?> holder;                 // 非空 = vanilla 预览
    private final RecipeViewerEngine.JeiEntry jei;        // 非空 = JEI 1:1*0.6 预览
    private final int mode;
    private final boolean craftable;
    private final boolean partial;
    private final int width;
    private final int height;

    public RecipePreviewTooltipComponent(RecipeHolder<?> holder,
                                         RecipeViewerEngine.JeiEntry jei, int mode) {
        this(holder, jei, mode, false, false);
    }

    public RecipePreviewTooltipComponent(RecipeHolder<?> holder,
                                         RecipeViewerEngine.JeiEntry jei, int mode,
                                         boolean craftable, boolean partial) {
        this.holder = holder;
        this.jei = jei;
        this.mode = mode;
        this.craftable = craftable;
        this.partial = partial;
        if (jei != null && jei.layoutWidth() > 0 && jei.layoutHeight() > 0) {
            this.width = Math.round(jei.layoutWidth() * TOOLTIP_SCALE) + PADDING * 2;
            this.height = Math.round(jei.layoutHeight() * TOOLTIP_SCALE) + PADDING * 2;
        } else {
            this.width = 48;
            this.height = 48;
        }
    }

    @Override
    public int getWidth(Font font) {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix,
                           MultiBufferSource.BufferSource buffer) {
        // 一切在 renderImage 画
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics gui) {
        if (jei != null) {
            // JEI 条目：0.6 缩放完整 UI（经 headless-jei 1:1 委托路径缩放）
            int lw = Math.max(1, Math.round(jei.layoutWidth() * TOOLTIP_SCALE));
            int lh = Math.max(1, Math.round(jei.layoutHeight() * TOOLTIP_SCALE));
            int[] rect = PopupRenderer.renderJeiPopupScaled(gui, jei,
                    x + PADDING, y + PADDING, lw, lh);
            if (rect == null && holder != null) {
                // 无布局/无 JEI 运行时：固定布局回退（仅当有 holder 时——纯 JEI
                // 条目且 rendjer 缺席会 NPE）。
                PopupRenderer.renderRecipePopup(gui, holder, mode, craftable, partial,
                        x + 12, y + 12, 24, 24, true, PopupGeometry.VANILLA_SCALE);
            }
        } else if (holder != null) {
            // vanille：居中 24x24 按钮，由弹窗自身 2x 变换放大到 48x48。
            // hover=true + VANILLA_SCALE（1.21.11 语义）。
            PopupRenderer.renderRecipePopup(gui, holder, mode, craftable, partial,
                    x + 12, y + 12, 24, 24, true, PopupGeometry.VANILLA_SCALE);
        }
    }
}
