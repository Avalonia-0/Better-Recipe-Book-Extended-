package com.alonie.brbe.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 1.21.1 版富 tooltip 组件基座（1.21.11 station/标题行组件的 1.21.1 适配）。
 *
 * <p>1.21.1 的 {@link ClientTooltipComponent} 接口与 1.21.11 不同：
 * {@code renderText(Font, int, int, Matrix4f, MultiBufferSource.BufferSource)}
 * 先跑（文本 pass，带矩阵），{@code renderImage(Font, int, int, GuiGraphics)}
 * 后跑（图像 pass）。文本经 {@code font.drawInBatch(...)} 绘入矩阵。</p>
 */
public abstract class AbstractBrbeTooltipComponent implements ClientTooltipComponent {

    static final int LINE_HEIGHT = 10;
    static final int ICON_SIZE = 16;
    static final int GAP = 2;

    protected static void drawText(Font font, FormattedCharSequence text, int x, int y,
                                   int color, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        font.drawInBatch(text, x, y, color, true, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 0xFFFFFF);
    }

    protected static void drawTextCentered(Font font, FormattedCharSequence text, int x, int y,
                                           int color, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        font.drawInBatch(text, x, y, color, true, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 0xFFFFFF);
    }

    /** 文本行 + 右侧图标行（工作站行/燃料行）。 */
    public static final class StationLine extends AbstractBrbeTooltipComponent {
        private final List<Segment> segments;

        public record Segment(FormattedCharSequence text, List<ItemStack> icons, boolean dotBelow) {}

        public StationLine(List<Segment> segments) {
            this.segments = segments;
        }

        @Override
        public int getWidth(Font font) {
            int w = 0;
            for (Segment segment : segments) {
                w += font.width(segment.text()) + GAP;
                if (segment.icons() != null && !segment.icons().isEmpty()) {
                    w += GAP + ICON_SIZE * segment.icons().size();
                }
            }
            return Math.max(0, w - GAP);
        }

        @Override
        public int getHeight() {
            int h = LINE_HEIGHT + GAP * 2;
            for (Segment segment : segments) {
                if (segment.dotBelow()) h += LINE_HEIGHT;
            }
            return h;
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix,
                               MultiBufferSource.BufferSource buffer) {
            int cx = x;
            for (Segment segment : segments) {
                drawText(font, segment.text(), cx, y, -1, matrix, buffer);
                cx += font.width(segment.text()) + GAP;
                if (segment.icons() != null && !segment.icons().isEmpty()) {
                    cx += GAP + ICON_SIZE * segment.icons().size();
                }
            }
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics gui) {
            int cx = x;
            int iy = y + GAP;
            for (Segment segment : segments) {
                cx += font.width(segment.text()) + GAP;
                if (segment.icons() != null && !segment.icons().isEmpty()) {
                    int iconY = iy + (Math.max(LINE_HEIGHT, ICON_SIZE) - ICON_SIZE) / 2;
                    for (ItemStack icon : segment.icons()) {
                        if (icon != null && !icon.isEmpty()) {
                            gui.renderItem(icon, cx, iconY);
                        }
                        cx += ICON_SIZE;
                    }
                }
                if (segment.dotBelow()) {
                    // 白点（当前站标记）已由文本行处理——此处留空
                }
            }
        }
    }

    /** 标题 + 左侧图标（tooltip 首行）。 */
    public static final class TitleWithIcon extends AbstractBrbeTooltipComponent {
        private final FormattedCharSequence title;
        private final ItemStack icon;

        public TitleWithIcon(FormattedCharSequence title, ItemStack icon) {
            this.title = title;
            this.icon = icon;
        }

        @Override
        public int getWidth(Font font) {
            return ICON_SIZE + 3 + font.width(title);
        }

        @Override
        public int getHeight() {
            return Math.max(LINE_HEIGHT, ICON_SIZE) + GAP * 2;
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix,
                               MultiBufferSource.BufferSource buffer) {
            int ty = y + GAP + (Math.max(LINE_HEIGHT, ICON_SIZE) - LINE_HEIGHT) / 2;
            drawText(font, title, x + ICON_SIZE + 3, ty, -1, matrix, buffer);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics gui) {
            int iy = y + GAP + (Math.max(LINE_HEIGHT, ICON_SIZE) - ICON_SIZE) / 2;
            if (icon != null && !icon.isEmpty()) {
                gui.renderItem(icon, x, iy);
            }
        }
    }

    /** 文本 + 右锚点标记（tab 滑动 ◀ ▶ / 站列 ▲ ▼）。 */
    public static final class MarkerLine extends AbstractBrbeTooltipComponent {
        private final FormattedCharSequence text;
        private final FormattedCharSequence marker;
        private final int anchorX;

        public MarkerLine(FormattedCharSequence text, FormattedCharSequence marker, int anchorX) {
            this.text = text;
            this.marker = marker;
            this.anchorX = anchorX;
        }

        @Override
        public int getWidth(Font font) {
            return anchorX + font.width(marker);
        }

        @Override
        public int getHeight() {
            return LINE_HEIGHT + GAP * 2;
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix,
                               MultiBufferSource.BufferSource buffer) {
            drawText(font, text, x, y + GAP, -1, matrix, buffer);
            drawText(font, marker, x + anchorX, y + GAP, -1, matrix, buffer);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics gui) {
        }
    }
}
