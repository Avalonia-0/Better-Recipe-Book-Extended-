package com.alonie.brbe.util;

import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * 配置界面**两侧的竖排像素字**（屏幕级覆盖绘制，零 mixin —— 用户 2026-09-12 选定的 B 方案）。
 *
 * <p>用户需求：左侧竖排 {@value #LEFT_TEXT}、右侧竖排 {@value #RIGHT_TEXT}，逐字符纵向排列
 * （字符本身保持正立、只改排布），首字符顶着上方横线、末字符顶着下方横线；字号与字距可调，
 * 窗口缩放后要跟着刷新；两侧各列**逐字横向之字形摆动**（奇数字向左、偶数字向右，幅度 5~8px
 * 随机，每次刷新重掷）。</p>
 *
 * <p><b>为什么两侧有位置</b>：Cloth 的配置列表虽然占满屏幕宽（{@code left = 0}、
 * {@code right = width}），但**行**只画在 {@code getRowLeft() .. +getItemWidth()} 这一段 ——
 * 两者都从字节码核实过：{@code renderList} 用 {@code getRowLeft()} 当 x、
 * {@code getItemWidth()} 当宽，而 {@code ClothConfigScreen$ListWidget} 覆写了
 * {@code getItemWidth() = width - 80}、{@code getRowLeft() = left + width/2 - getItemWidth()/2 + 2}
 * → 行恒定落在 {@code 42 .. width - 38}，滚动条在 {@code width - 36}。
 * 左右因此各留下一条约 42px / 38px 的空白竖条，正好放这种装饰文字（不会压到任何行内容）。</p>
 *
 * <p><b>两列的中心线</b>：取"屏幕边界"与"内容区边界"的**正中** ——
 * 左列 = {@code (list.left + rowLeft)/2}（屏幕左边界与内容左边界的正中），
 * 右列 = {@code (rowRight + list.right)/2}（内容右边界与屏幕右边界的正中）。
 * 需要再往右/往左挪就改 {@link #RIGHT_SHIFT_PX}。</p>
 *
 * <p><b>纵向范围</b>：{@code listWidget.top}（类别栏下方的横线）到
 * {@code listWidget.bottom = height - 32}（底部按钮栏上方的横线）—— 两者都是
 * {@link DynamicEntryListWidget} 的 public 字段，无需反射。</p>
 *
 * <p><b>为什么每帧重算布局</b>：位置只由字体度量与当前屏幕尺寸决定，几十次 {@code font.width(...)}
 * 的开销可以忽略；换来的是**窗口缩放 / GUI Scale 变化自动跟随**，不必监听 resize
 * （Cloth 的列表尺寸在每次 {@code init()} 里重新算好，我们直接读当前值）。
 * 但**之字形偏移不每帧重掷**（那样会 60fps 抖动），只在 {@link #onScreenInit} 时重掷一次 ——
 * 打开界面 / 切类别 / 缩放都会走那里。</p>
 *
 * <p><b>注册方式</b>：本类**不引用任何加载器 API**（1.21.1 的 common 模块也要编译它），
 * 事件注册在各分支入口完成 —— fabric 用 {@code ScreenEvents.afterExtract}（26.2）/
 * {@code ScreenEvents.afterRender}（1.21.11、1.21.1-fabric），neoforge 用
 * {@code ScreenEvent.Render.Post}；{@link #onScreenInit} 由各自的屏幕初始化事件调用。</p>
 */
public final class ConfigScreenSideText {

    // ── 可调参数 ────────────────────────────────────────────────────────────

    /** 左侧竖排文字。 */
    private static final String LEFT_TEXT = "Better Recipe Book";
    /** 右侧竖排文字。 */
    private static final String RIGHT_TEXT = "Adorable♡Girl aVa Seriously Extended";
    /** 文字颜色（ARGB）：**透明度 25%**（alpha 0x40 = 64/255）+ 中灰，纯水印观感；
     *  不画阴影，像素字更干净。要更淡/更亮就改这一行（前两位是 alpha）。 */
    private static final int TEXT_COLOR = 0x40A0A0A0;
    /** 是否带阴影（原版字体阴影）。 */
    private static final boolean TEXT_SHADOW = false;

    /** 字号上限（倍）：越大字号随窗口长得越猛。1.0 = 原版 8px 像素字的 1 倍；
     *  取 2.0 时普通窗口下左列约 13~18px、右列约 7~11px。 */
    private static final float MAX_SCALE = 2.0F;
    /** 字号吸附粒度：字号会向下取到 1/4 的整数倍（1.0 / 0.75 / 0.5 / 0.25），
     *  避免分数缩放把像素字糊掉。 */
    private static final int SCALE_STEPS = 4;
    /** 字间空隙系数：1.0 = 字符首尾相接，&gt;1 = 留出空隙。
     *  真正的位置仍由"首字符顶上线、末字符顶下线"决定 —— 该系数只影响字号取值。 */
    private static final float LEFT_SPACING = 1.10F;
    private static final float RIGHT_SPACING = 1.06F;
    /** 纵向微调（px）：像素字的墨迹在字符格里略偏上，需要时用这两个值压一压。 */
    private static final int TOP_NUDGE = 0;
    private static final int BOTTOM_NUDGE = 0;

    /** 之字形横向摆动的幅度范围（px，**含两端**）：第 1 个字向左、第 2 个向右、第 3 个向左……
     *  每个字的幅度独立随机。0 / 0 = 关掉摆动（两列回到竖直的一条线）。 */
    private static final int SWAY_MIN_PX = 5;
    private static final int SWAY_MAX_PX = 8;
    /** 右列中心线的额外位移（px）：正数更靠右。按"内容右边界与屏幕右边界的正中"算出来是 0。 */
    private static final int RIGHT_SHIFT_PX = 0;

    // ── 状态 ────────────────────────────────────────────────────────────────

    private static final Random RANDOM = new Random();

    /** 每个屏幕当前这一"刷"的横向摆动量：{@code [0] = 左列}、{@code [1] = 右列}，
     *  下标与**码点**一一对应。键是弱引用，界面关掉后自动回收。 */
    private static final Map<Screen, int[][]> SWAY = new WeakHashMap<>();

    private ConfigScreenSideText() {
    }

    /** 入口侧过滤：只有 Cloth 的配置界面才画。（Cloth 是可选依赖，缺失时这里永远为 false。） */
    public static boolean shouldRender(Screen screen) {
        return screen instanceof ClothConfigScreen;
    }

    /**
     * 屏幕初始化时调用（打开配置界面 / 切类别 / 缩放都会重跑 {@code init()}）：
     * **重新掷一次两列的之字形偏移**。必须在首次渲染之前调用（入口挂在屏幕初始化事件上）。
     */
    public static void onScreenInit(Screen screen) {
        if (!shouldRender(screen)) return;
        SWAY.put(screen, new int[][] {rollSway(LEFT_TEXT), rollSway(RIGHT_TEXT)});
    }

    /**
     * 绘制两侧竖排文字。由屏幕级渲染回调在**整屏渲染完成后**调用。
     */
    public static void render(Screen screen, GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        if (!(screen instanceof ClothConfigScreen cloth)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        DynamicEntryListWidget<?> list = (DynamicEntryListWidget<?>) (Object) cloth.listWidget;
        if (list == null || list.height <= 0) return;

        // 行内容的左右边界（Cloth 的 ListWidget 把行收窄：getItemWidth() = width - 80，
        // getRowLeft() = left + width/2 - getItemWidth()/2 + 2）——两侧剩下的就是我们的竖条。
        int rowLeft = list.left + list.width / 2 - list.getItemWidth() / 2 + 2;
        int rowRight = rowLeft + list.getItemWidth();
        if (rowRight <= rowLeft) return;

        int top = list.top + TOP_NUDGE;
        int bottom = list.bottom - BOTTOM_NUDGE;
        if (bottom - top < 8) return;

        // 没有初始化记录时兜底现掷一次（正常路径由 onScreenInit 负责）。
        int[][] sway = SWAY.get(screen);
        if (sway == null) {
            sway = new int[][] {rollSway(LEFT_TEXT), rollSway(RIGHT_TEXT)};
            SWAY.put(screen, sway);
        }

        // 中心线 = 屏幕边界与内容区边界的正中
        int leftCenter = (list.left + rowLeft) / 2;
        int rightCenter = (rowRight + list.right) / 2 + RIGHT_SHIFT_PX;

        drawColumn(gui, mc.font, LEFT_TEXT, leftCenter, top, bottom, LEFT_SPACING, sway[0]);
        drawColumn(gui, mc.font, RIGHT_TEXT, rightCenter, top, bottom, RIGHT_SPACING, sway[1]);
    }

    /** 掷一列的横向摆动：奇数字（下标 0、2、…）向左、偶数字向右，幅度各自独立随机。 */
    private static int[] rollSway(String text) {
        int n = text.codePointCount(0, text.length());
        int[] out = new int[n];
        if (SWAY_MAX_PX <= 0) return out;                       // 摆动关闭
        int span = Math.max(0, SWAY_MAX_PX - SWAY_MIN_PX) + 1;   // 含两端
        for (int i = 0; i < n; i++) {
            int mag = SWAY_MIN_PX + RANDOM.nextInt(span);
            out[i] = (i % 2 == 0) ? -mag : mag;
        }
        return out;
    }

    /**
     * 在一条竖列里自上而下逐字符画 {@code text}：
     * <b>首字符顶端 = {@code top}，末字符底端 = {@code bottom}</b>（正好顶着上下两条横线），
     * 每个字符在自己的格里水平居中，再按 {@code sway} 逐个左右错开。
     *
     * <p>字号先按"字符紧贴 + 字距系数"求能放下的最大值，再向下吸附到
     * {@code 1/SCALE_STEPS} 的整数倍；因此屏幕越高字越大、越矮字越小，
     * 但**始终首末顶格**。</p>
     */
    private static void drawColumn(GuiGraphics gui, Font font, String text,
                                   int centerX, int top, int bottom, float spacing, int[] sway) {
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) return;

        float span = bottom - top;
        // 需要的总高（含字距系数）= lineHeight * scale * (1 + (n-1) * spacing) → 反解 scale
        float raw = span / (font.lineHeight * (1.0F + (cps.length - 1) * spacing));
        float scale = Math.min(MAX_SCALE, snapDown(raw));
        if (scale <= 0.0F) scale = 1.0F / SCALE_STEPS;

        float glyphH = font.lineHeight * scale;
        // 首字顶 top、末字底 bottom → 步长（n == 1 时无步长）
        float step = cps.length > 1 ? (span - glyphH) / (cps.length - 1) : 0.0F;

        for (int i = 0; i < cps.length; i++) {
            String glyph = new String(Character.toChars(cps[i]));
            float w = font.width(glyph) * scale;
            int offset = (sway != null && i < sway.length) ? sway[i] : 0;
            float x = centerX + offset - w / 2.0F;
            float y = top + i * step;
            gui.pose().pushPose();
            gui.pose().translate(x, y, 0);
            gui.pose().scale(scale, scale, 1.0F);
            gui.drawString(font, glyph, 0, 0, TEXT_COLOR, TEXT_SHADOW);
            gui.pose().popPose();
        }
    }

    /** 向下吸附到 {@code 1/SCALE_STEPS} 的整数倍（0.25 粒度），保证像素字不被分数缩放糊掉。 */
    private static float snapDown(float scale) {
        int steps = (int) Math.floor(scale * SCALE_STEPS);
        return steps / (float) SCALE_STEPS;
    }
}
