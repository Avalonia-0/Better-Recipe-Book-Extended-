package com.alonie.brbe.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * LEI 查询窗口那对翻页键的**复用绘制**（用户 2026-09-25 诉求：替代配方组也要分页，
 * 按钮位置与 LEI 类似、用同一贴图）。
 *
 * <p>贴图就是 LEI 用的 {@code recipe_book/lei_page_button.png}（256×256 左上角 4×2 格，
 * 每格 14×13）：{@code u=0} 左键位、{@code u=14} 右键位、{@code u+=28} 悬停变体、
 * {@code v=13} 禁用变体。</p>
 *
 * <h3>角标为什么"裁切后交换"</h3>
 * <p>LEI 的两格角标是 <b>左 ▼（下三角）、右 ▲（上三角）</b>；替代配方组按用户要求要
 * <b>左 ▲、右 ▼</b>。做法不是把两格贴图整体对调——那样连底板里"朝左/朝右"的 1px 差异
 * （左键底板在 {@code x=1..13}、右键在 {@code x=0..12}）也会跟着换，底板就不对了。所以：
 * <b>底板仍取本按钮自己那一格</b>，随后把<b>另一格</b>中心三角所在的矩形
 * （局部 {@code 2,3,8,8}）裁下来、贴回同一局部位置——只换角标，底板不动。</p>
 *
 * <p>禁用态（{@code v=13}）与悬停态（{@code u+=28}）的裁切源与底板取同一状态，
 * 所以裁下来的那圈底色与目标按钮的底色一致，接缝看不出来。</p>
 */
public final class LeiPageButtons {

    /** 与 LEI 查询窗口同一个贴图。 */
    private static final Identifier SPRITE = Identifier.fromNamespaceAndPath("brbe",
            "textures/gui/sprites/recipe_book/lei_page_button.png");

    public static final int WIDTH = 14;
    public static final int HEIGHT = 13;
    /** 两键左缘间距（LEI 同值：{@code bx} 与 {@code bx + 15}）。 */
    public static final int GAP = 15;
    /** 一对按钮的总宽（左缘到右键右缘）。 */
    public static final int PAIR_WIDTH = GAP + WIDTH;
    /** 右键右缘与面板右缘的距离。LEI 原值 4；用户 2026-09-26 要求整体右移 5px → 4-5 = -1
     *  （即略微探出面板右缘 1px）。 */
    public static final int RIGHT_MARGIN = -1;
    /** 按钮顶边在面板顶边之上多少像素。LEI 原值 10（{@code -HEIGHT - 2 + SHIFT_Y}）；
     *  用户 2026-09-26 要求整体上移 5px → 15。 */
    public static final int ABOVE = 15;

    /**
     * 中心角标在 14×13 格内的局部矩形。两格的**三角各偏 1px**（左格底板 x=1..13、三角
     * x=3..9；右格底板 x=0..12、三角 x=2..8，各自相对底板都是"中心偏左 1px"），所以裁切与
     * 贴回要**各挪 1px**：从左格裁时起点 {@code x=3} 贴到 {@code x=2}，从右格裁时起点
     * {@code x=2} 贴到 {@code x=3}——这样三角落在本按钮底板的同心位置（此前不挪 → 偏 1px，
     * 用户 2026-09-26 反馈"裁切的好像不太准"）。
     */
    private static final int MARK_Y = 3;
    /** 裁切宽度：用户 2026-09-26 要求再**向右拓宽 1px**（8 → 9），避免悬停态三角右缘被切掉。 */
    private static final int MARK_W = 9;
    private static final int MARK_H = 8;
    /** 从"左键格"裁角标时的源 x（贴到 {@link #MARK_DST_FROM_LEFT}）。 */
    private static final int MARK_SRC_FROM_LEFT = 3;
    /** 从"右键格"裁角标时的源 x（贴到 {@link #MARK_DST_FROM_RIGHT}）。 */
    private static final int MARK_SRC_FROM_RIGHT = 2;
    private static final int MARK_DST_FROM_LEFT = 2;
    private static final int MARK_DST_FROM_RIGHT = 3;

    private LeiPageButtons() {
    }

    /** 一对按钮的左缘 x（面板右对齐）。 */
    public static int leftX(int panelX, int panelW) {
        return panelX + panelW - PAIR_WIDTH - RIGHT_MARGIN;
    }

    /** 一对按钮的顶边 y（面板顶边之上 {@link #ABOVE} 像素）。 */
    public static int topY(int panelY) {
        return panelY - ABOVE;
    }

    /**
     * 画一对翻页键（面板之上、右对齐）。
     *
     * @param leftX      左键左缘（{@link #leftX}）
     * @param topY       两键顶边（{@link #topY}）
     * @param prevActive 左键是否可点（首页时禁用 → 画禁用变体）
     * @param nextActive 右键是否可点（末页时禁用）
     */
    public static void draw(GuiGraphics gui, int leftX, int topY, int mouseX, int mouseY,
                            boolean prevActive, boolean nextActive, int page, int pageCount) {
        boolean overPrev = prevActive && over(leftX, topY, mouseX, mouseY);
        boolean overNext = nextActive && over(leftX + GAP, topY, mouseX, mouseY);
        // 左键画 ▲、右键画 ▼（角标交换；底板各自不变）
        drawButton(gui, leftX, topY, true, prevActive, overPrev);
        drawButton(gui, leftX + GAP, topY, false, nextActive, overNext);
        if (overPrev || overNext) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                gui.setTooltipForNextFrame(mc.font, Component.literal((page + 1) + "/" + pageCount),
                        mouseX, mouseY, ClientCompat.VIEWER_TOOLTIP_STYLE);
            }
        }
    }

    /** 左键（prev）命中？ */
    public static boolean overPrev(int leftX, int topY, int mouseX, int mouseY) {
        return over(leftX, topY, mouseX, mouseY);
    }

    /** 右键（next）命中？ */
    public static boolean overNext(int leftX, int topY, int mouseX, int mouseY) {
        return over(leftX + GAP, topY, mouseX, mouseY);
    }

    private static boolean over(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT;
    }

    /**
     * 画单个翻页键。
     *
     * @param leftButton true = 这一格是"左键位"（角标要从右键位裁过来）
     * @param active     false = 禁用变体（{@code v=13}）
     */
    private static void drawButton(GuiGraphics gui, int x, int y, boolean leftButton,
                                   boolean active, boolean hovered) {
        int u = leftButton ? 0 : 14;
        if (hovered) {
            u += 28;
        }
        int v = active ? 0 : 13;
        gui.blit(RenderPipelines.GUI_TEXTURED, SPRITE, x, y, u, v, WIDTH, HEIGHT, 256, 256);

        // 角标交换：裁切**另一格**的中心三角，贴回本按钮底板的同心位置（底板保持本格）。
        // 源格与目标格各挪 1px（见 MARK_* 常量）——不挪会偏 1px。
        int markU = (leftButton ? 14 : 0) + (hovered ? 28 : 0);
        int srcX = leftButton ? MARK_SRC_FROM_RIGHT : MARK_SRC_FROM_LEFT;
        int dstX = leftButton ? MARK_DST_FROM_RIGHT : MARK_DST_FROM_LEFT;
        gui.blit(RenderPipelines.GUI_TEXTURED, SPRITE, x + dstX, y + MARK_Y,
                markU + srcX, v + MARK_Y, MARK_W, MARK_H, 256, 256);
    }
}
