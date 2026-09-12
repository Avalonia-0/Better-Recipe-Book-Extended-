package com.alonie.brbe.util;

import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * 配置界面**两侧的竖排像素字**（屏幕级覆盖绘制，零 mixin —— 用户 2026-09-12 选定的 B 方案）。
 *
 * <p>用户需求：左侧竖排 {@value #LEFT_TEXT}、右侧竖排 {@value #RIGHT_TEXT}，逐字符纵向排列
 * （字符本身保持正立、只改排布），
 * 首字符顶着上方横线、末字符顶着下方横线；字号与字距可调，窗口缩放后要跟着刷新；
 * 每个字再**逐个横向之字形摆动**（奇数字向左、偶数字向右，幅度 2~8px 随机）并**逐个随机倾斜**
 * （方向顺/逆时针随机、角度 2~12° 随机）—— 两者都是每字独立随机、**每次刷新重掷**，两侧同款。</p>

 * <p><b>分片上色</b>：所有字都是 10% 透明，只有色相不同。</p>
 *
 * <ul>
 *   <li><b>固定片段</b>：按 {@link #LEFT_TINTS} / {@link #RIGHT_TINTS} 的规则换色 ——
 *       "Recipe Book" 绿、♡ 粉、"aVa" 蓝。</li>
 *   <li><b>其余字段</b>：按「<b>一个单词 = 一个字段</b>」切分，每个字段<b>独立</b>从
 *       {@link #RANDOM_PALETTE}（黄 / 紫 / 橙 / 白）里随机取一色；<b>每次刷新重新分配</b>
 *       （打开界面 / 切类别 / 缩放都会走 {@link #onScreenInit}，与摆动、倾斜同一时机）。</li>
 * </ul>
 *
 * <p><b>为什么两侧都有位置</b>：Cloth 的配置列表虽然占满屏幕宽（{@code left = 0}、
 * {@code right = width}），但**行**只画在 {@code getRowLeft() .. +getItemWidth()} 这一段 ——
 * 两者都从字节码核实过：{@code renderList} 用 {@code getRowLeft()} 当 x、
 * {@code getItemWidth()} 当宽，而 {@code ClothConfigScreen$ListWidget} 覆写了
 * {@code getItemWidth() = width - 80}、{@code getRowLeft() = left + width/2 - getItemWidth()/2 + 2}
 * → 行恒定落在 {@code 42 .. width - 38}。左右因此各留下一条约 42px / 38px 的空白竖条，
 * 正好放这种装饰文字（不会压到任何行内容）。</p>
 *
 * <p><b>中心线</b>：取"屏幕边界"与"内容区边界"的**正中** —— 左列
 * {@code (list.left + rowLeft) / 2}（≈ 21px 处）、右列 {@code (rowRight + list.right) / 2}
 * 再按 {@link #RIGHT_SHIFT_PX} 额外右移（当前 +4px）。</p>
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
 * <p><b>字号</b>：<b>两列共用同一个字号</b>，以字少的<b>左列</b>为基准（{@link #naturalScale}）——
 * 右列字数约为左列两倍，塞进同样的高度意味着步长小于字格高、相邻字在<b>纵向</b>上叠排，
 * 靠逐字之字形摆动（{@link #SWAY_MIN_PX}~{@link #SWAY_MAX_PX}）左右错开（用户 2026-09-13
 * 要求"右侧文字的字符大小直接同步左侧"）。</p>
 *
 * <p><b>开关</b>：「杂项」页的<b>隐藏配置界面两侧的文字</b>（{@code hideConfigSideText}，默认关）
 * 打开时 {@link #shouldRender} 与 {@link #render} 都直接不画 —— fabric 入口的按屏注册因此压根不会发生，
 * NeoForge 的全局监听也在这里被挡下。</p>
 *
 * <p><b>注册方式</b>：本类**不引用任何加载器 API**（1.21.1 的 common 模块也要编译它），
 * 事件注册在各分支入口完成 —— fabric 用 {@code ScreenEvents.afterExtract}（26.2）/
 * {@code ScreenEvents.afterRender}（1.21.11、1.21.1-fabric），neoforge 用
 * {@code ScreenEvent.Render.Post}；{@link #onScreenInit} 由各自的屏幕初始化事件调用。</p>
 */
public final class ConfigScreenSideText {

    // ── 可调参数 ────────────────────────────────────────────────────────────

    /** 竖排文字（左侧）。 */
    private static final String LEFT_TEXT = "Better Recipe Book";
    /** 竖排文字（右侧）。 */
    private static final String RIGHT_TEXT = "Adorable♡Girl aVa Seriously Extended";
    /** 左列的码点数 —— <b>两列共用的字号基准</b>（右列字号直接同步它，见 {@link #naturalScale}）。 */
    private static final int LEFT_LENGTH = LEFT_TEXT.codePointCount(0, LEFT_TEXT.length());
    /** 默认字色（ARGB，中灰）：**透明度 10%**（alpha 0x1A = 26/255 ≈ 10.2%），纯水印观感；
     *  不画阴影，像素字更干净。要更淡/更亮就改前两位 alpha
     *  （0x1A=10% / 0x26=15% / 0x33=20% / 0x40=25% / 0x59=35%）。 */
    private static final int COLOR_DEFAULT = 0x1AA0A0A0;
    /** 固定片段的色相（同样保持 10% 透明，只有色相不同）。 */
    private static final int COLOR_PINK = 0x1AFF77CC;      // ♡
    private static final int COLOR_BLUE = 0x1A77B7FF;      // aVa
    private static final int COLOR_GREEN = 0x1A77FF77;     // Recipe Book
    /** 左列固定上色规则：把 "Recipe Book" 染绿。 */
    private static final List<Tint> LEFT_TINTS = List.of(
            new Tint("Recipe Book", COLOR_GREEN));
    /** 右列固定上色规则：♡ 染粉、aVa 染蓝（两条规则命中的片段互不重叠）。 */
    private static final List<Tint> RIGHT_TINTS = List.of(
            new Tint("♡", COLOR_PINK),
            new Tint("aVa", COLOR_BLUE));
    /** 剩余字段的随机色池：黄 / 紫 / 橙 / 白（每个字段独立抽签，每次刷新重抽）。 */
    private static final int[] RANDOM_PALETTE = {
            0x1AFFFF55,     // 黄
            0x1AFF55FF,     // 紫
            0x1AFFAA00,     // 橙
            0x1AFFFFFF,     // 白
    };
    /** "还没分配色相"的哨兵值：固定规则没命中的位置留它，交给 {@link #rollColors} 抽签。 */
    private static final int UNASSIGNED = 0;
    /** 两列的**固定**色表（类初始化时算一次，下标与**码点**一一对应；未命中规则处为 {@link #UNASSIGNED}）。 */
    private static final int[] LEFT_FIXED = tintArray(LEFT_TEXT, LEFT_TINTS);
    private static final int[] RIGHT_FIXED = tintArray(RIGHT_TEXT, RIGHT_TINTS);
    /** 是否带阴影（原版字体阴影）。 */
    private static final boolean TEXT_SHADOW = false;

    /** 字号上限（倍）：越大字号随窗口长得越猛。1.0 = 原版 8px 像素字的 1 倍；
     *  取 2.0 时普通窗口下约 13~18px。 */
    private static final float MAX_SCALE = 2.0F;
    /** 字号吸附粒度：字号会向下取到 1/4 的整数倍（1.0 / 0.75 / 0.5 / 0.25），
     *  避免分数缩放把像素字糊掉。 */
    private static final int SCALE_STEPS = 4;
    /** 字间空隙系数：1.0 = 字符首尾相接，&gt;1 = 留出空隙。真正的位置仍由"首字符顶上线、
     *  末字符顶下线"决定 —— 该系数只影响**字号基准列（左列）**的字号取值。
     *  右列没有独立系数：它的字号直接同步左列（用户 2026-09-13 要求）。 */
    private static final float LEFT_SPACING = 1.10F;
    /** 纵向微调（px）：像素字的墨迹在字符格里略偏上，需要时用这两个值压一压。 */
    private static final int TOP_NUDGE = 0;
    private static final int BOTTOM_NUDGE = 0;

    /** 之字形横向摆动的幅度范围（px，**含两端**）：第 1 个字向左、第 2 个向右、第 3 个向左……
     *  每个字的幅度独立随机。0 / 0 = 关掉摆动（回到竖直的一条线）。 */
    private static final int SWAY_MIN_PX = 2;
    private static final int SWAY_MAX_PX = 8;
    /** 逐字随机倾斜的角度范围（度，**含两端**）：方向（顺时针 / 逆时针）也逐字随机。
     *  0 / 0 = 关掉倾斜。 */
    private static final int ROTATE_MIN_DEG = 2;
    private static final int ROTATE_MAX_DEG = 12;
    /** 右列中心线的额外位移（px）：正数更靠右（按"内容右边界与屏幕右边界的正中"算出来是 0）。 */
    private static final int RIGHT_SHIFT_PX = 4;

    // ── 状态 ────────────────────────────────────────────────────────────────

    private static final Random RANDOM = new Random();

    /** 每个屏幕当前这一"刷"的随机装饰量，{@code [0] = 左列}、{@code [1] = 右列}；
     *  每列的数组下标与**码点**一一对应。键是弱引用，界面关掉后自动回收。 */
    private static final Map<Screen, Roll[]> ROLLS = new WeakHashMap<>();

    /** 一次"刷新"内固定的一组随机量：逐字偏移、逐字倾角，以及**本次分配**的逐字颜色。 */
    private record Roll(int[] swayPx, float[] angleRad, int[] colors) {
    }

    /** 一条固定上色规则：把 {@code text} 里出现的 {@code needle} 全部染成 {@code color}。 */
    private record Tint(String needle, int color) {
    }

    /**
     * 生成**固定**逐字颜色表：先全填 {@link #UNASSIGNED}，再让每条 {@link Tint} 规则覆盖它命中的码点。
     * 文案与规则都是常量，所以只需在类初始化时算一次；剩下的 {@link #UNASSIGNED} 位置留给
     * {@link #rollColors} 每次刷新时抽签。
     */
    private static int[] tintArray(String text, List<Tint> tints) {
        int[] out = new int[text.codePointCount(0, text.length())];
        Arrays.fill(out, UNASSIGNED);
        for (Tint tint : tints) {
            int from = 0;
            while (true) {
                int at = text.indexOf(tint.needle(), from);
                if (at < 0) break;
                int start = text.codePointCount(0, at);
                int len = tint.needle().codePointCount(0, tint.needle().length());
                for (int i = start; i < start + len && i < out.length; i++) out[i] = tint.color();
                from = at + tint.needle().length();
            }
        }
        return out;
    }

    /**
     * 在固定色表的基础上分配**剩余字段**的颜色：把连续、未被固定规则命中、且非空白的码点视为
     * 一个<b>单词</b>（= 一个字段），逐字段从 {@link #RANDOM_PALETTE} 里独立抽一色 ——
     * 所以同一列里两个单词完全可能撞色，也可能全不同；每次刷新重抽。
     *
     * <p>空白字符不参与抽签（本身画不出颜色），按 {@link #COLOR_DEFAULT} 处理。</p>
     */
    private static int[] rollColors(String text, int[] fixedColors) {
        int[] cps = text.codePoints().toArray();
        int[] out = fixedColors.clone();
        int start = -1;                       // 当前单词的起点；-1 = 当前不在单词里
        for (int i = 0; i <= cps.length; i++) {
            boolean inWord = i < cps.length
                    && out[i] == UNASSIGNED
                    && !Character.isWhitespace(cps[i]);
            if (inWord) {
                if (start < 0) start = i;
            } else if (start >= 0) {
                int color = RANDOM_PALETTE[RANDOM.nextInt(RANDOM_PALETTE.length)];
                Arrays.fill(out, start, i, color);
                start = -1;
            }
        }
        // 兜底：剩下的未分配位置（空白字符；正常不会有别的）用默认灰。
        for (int i = 0; i < out.length; i++) {
            if (out[i] == UNASSIGNED) out[i] = COLOR_DEFAULT;
        }
        return out;
    }

    private ConfigScreenSideText() {
    }

    /** 屏幕**类型**过滤：只有 Cloth 的配置界面才装饰（Cloth 是可选依赖，缺失时永远 false）。
     *
     *  <p>fabric 的三个入口用<b>本方法</b>决定要不要注册渲染监听 —— 它<b>不含开关判定</b>：
     *  开关是每帧在 {@link #render} 里判的，所以配置界面里直接切换「隐藏配置界面两侧的文字」
     *  两个方向都<b>立即生效</b>；若改用 {@link #shouldRender} 注册，开关开着时压根不会注册，
     *  关掉后要切类别 / 重开界面才回来（渲染监听只在 {@code Screen.init()} 时挂）。</p> */
    public static boolean isDecoratedScreen(Screen screen) {
        return screen instanceof ClothConfigScreen;
    }

    /** "此刻该不该画" = 屏幕类型对 + 「隐藏配置界面两侧的文字」没开。
     *  （{@link #render} 内部另有同一条兜底判定 —— NeoForge 走全局监听不过入口过滤。） */
    public static boolean shouldRender(Screen screen) {
        return isDecoratedScreen(screen) && !hiddenByConfig();
    }

    /** 「隐藏配置界面两侧的文字」是否开启（**默认关**；配置尚未注册时按关处理 = 照常绘制）。 */
    private static boolean hiddenByConfig() {
        return com.alonie.brbe.BetterRecipeBook.config != null
                && com.alonie.brbe.BetterRecipeBook.config.hideConfigSideText;
    }

    /**
     * 屏幕初始化时调用（打开配置界面 / 切类别 / 缩放都会重跑 {@code init()}）：
     * **重新掷一次左右偏移、倾斜角与字段颜色**。必须在首次渲染之前调用（入口挂在屏幕初始化事件上）。
     *
     * <p>按<b>屏幕类型</b>过滤（不看开关）：开关开着时也照常掷，玩家随后关掉开关就能立刻看到
     * 这一刷的颜色；即便一次都没掷过，{@link #render} 里也有"现掷一刷"的兜底。</p>
     */
    public static void onScreenInit(Screen screen) {
        if (!isDecoratedScreen(screen)) return;
        ROLLS.put(screen, new Roll[] { roll(LEFT_TEXT, LEFT_FIXED), roll(RIGHT_TEXT, RIGHT_FIXED) });
    }

    /**
     * 绘制左侧竖排文字。由屏幕级渲染回调在**整屏渲染完成后**调用。
     */
    public static void render(Screen screen, GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        if (!(screen instanceof ClothConfigScreen cloth)) return;
        if (hiddenByConfig()) return;                       // NeoForge 走全局监听，入口不过滤 → 这里兜住
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        DynamicEntryListWidget<?> list = (DynamicEntryListWidget<?>) (Object) cloth.listWidget;
        if (list == null || list.height <= 0) return;

        // 行内容的左边界（Cloth 的 ListWidget 把行收窄：getItemWidth() = width - 80，
        // getRowLeft() = left + width/2 - getItemWidth()/2 + 2）——左侧剩下的就是我们的竖条。
        int rowLeft = list.left + list.width / 2 - list.getItemWidth() / 2 + 2;
        if (rowLeft <= list.left) return;

        int top = list.top + TOP_NUDGE;
        int bottom = list.bottom - BOTTOM_NUDGE;
        if (bottom - top < 8) return;

        int rowRight = rowLeft + list.getItemWidth();

        // 没有初始化记录时兜底现掷一次（正常路径由 onScreenInit 负责）。
        Roll[] rolls = ROLLS.get(screen);
        if (rolls == null) {
            rolls = new Roll[] { roll(LEFT_TEXT, LEFT_FIXED), roll(RIGHT_TEXT, RIGHT_FIXED) };
            ROLLS.put(screen, rolls);
        }

        // 中心线 = 屏幕边界与内容区边界的正中（右列再按 RIGHT_SHIFT_PX 右移）
        int leftCenter = (list.left + rowLeft) / 2;
        int rightCenter = (rowRight + list.right) / 2 + RIGHT_SHIFT_PX;
        // 屏幕左右边界：摆到极限 + 大字号时也不让字形越出屏幕（正常参数用不到）
        int screenLeft = Math.min(0, list.left);
        int screenRight = Math.max(list.right, list.left + list.width);
        // 两列**共用同一个字号**：以字少的左列为基准（右列字号 = 左列字号，用户 2026-09-13）。
        // 右列 36 个字塞进同样的高度 → 步长被压到小于字格高，相邻字在纵向上叠排，
        // 但逐字之字形摆动把它们左右错开（相邻两字一左一右，横向至少差 4px）。
        float scale = naturalScale(mc.font, bottom - top, LEFT_LENGTH, LEFT_SPACING);
        drawColumn(gui, mc.font, LEFT_TEXT, leftCenter, top, bottom, rolls[0],
                scale, screenLeft, screenRight);
        drawColumn(gui, mc.font, RIGHT_TEXT, rightCenter, top, bottom, rolls[1],
                scale, screenLeft, screenRight);
    }

    /** 掷一列这一"刷"的随机量：字段颜色 + 左右偏移（奇数字向左、偶数字向右）+ 倾斜角（方向也逐字随机）。 */
    private static Roll roll(String text, int[] fixedColors) {
        int n = text.codePointCount(0, text.length());
        int[] sway = new int[n];
        float[] angle = new float[n];
        boolean swayOn = SWAY_MAX_PX > 0;
        boolean rotateOn = ROTATE_MAX_DEG > 0;
        int swaySpan = Math.max(1, SWAY_MAX_PX - SWAY_MIN_PX + 1);        // 含两端
        int rotateSpan = Math.max(1, ROTATE_MAX_DEG - ROTATE_MIN_DEG + 1);
        for (int i = 0; i < n; i++) {
            if (swayOn) {
                int mag = SWAY_MIN_PX + RANDOM.nextInt(swaySpan);
                sway[i] = (i % 2 == 0) ? -mag : mag;
            }
            if (rotateOn) {
                float deg = ROTATE_MIN_DEG + RANDOM.nextInt(rotateSpan);
                // 正角 = 屏幕上的顺时针（MC 的 y 轴朝下）；方向逐字独立随机
                angle[i] = (float) Math.toRadians(RANDOM.nextBoolean() ? deg : -deg);
            }
        }
        return new Roll(sway, angle, rollColors(text, fixedColors));
    }

    /**
     * 一列的**自然字号**：把 {@code n} 个字以 {@code spacing} 的字距塞进 {@code span} 高度所需的
     * 最大字号（{@code span / (lineHeight * (1 + (n-1) * spacing))}），再向下吸附到
     * {@code 1/SCALE_STEPS} 的整数倍、并以 {@link #MAX_SCALE} 封顶。
     *
     * <p>只用来算**字号基准列（左列）**的值，算出来的 scale 两列共用 —— 所以改这个函数的参数
     * （或 {@link #LEFT_SPACING} / {@link #MAX_SCALE}）会同时改两列的字号。</p>
     */
    private static float naturalScale(Font font, int span, int n, float spacing) {
        float raw = span / (font.lineHeight * (1.0F + (n - 1) * spacing));
        float scale = Math.min(MAX_SCALE, snapDown(raw));
        return scale <= 0.0F ? 1.0F / SCALE_STEPS : scale;
    }

    /**
     * 在竖列里自上而下逐字符画 {@code text}：
     * <b>首字符顶端 = {@code top}，末字符底端 = {@code bottom}</b>（正好顶着上下两条横线），
     * 每个字符在自己的格里水平居中，再按 {@code sway} 逐个左右错开。
     *
     * <p>字号由调用方给定（{@link #naturalScale}，**两列共用**同一值）：屏幕越高字越大、
     * 越矮字越小，但**始终首末顶格** —— 步长 = {@code (span - 字格高) / (字数 - 1)}，
     * 因此字数多的右列步长会小于字格高（字在纵向叠着排）。</p>
     */
    private static void drawColumn(GuiGraphicsExtractor gui, Font font, String text,
                                   int centerX, int top, int bottom, Roll roll, float scale,
                                   int screenLeft, int screenRight) {
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) return;
        int[] colors = (roll != null) ? roll.colors() : null;

        float span = bottom - top;
        float glyphH = font.lineHeight * scale;
        // 首字顶 top、末字底 bottom → 步长（n == 1 时无步长）
        float step = cps.length > 1 ? (span - glyphH) / (cps.length - 1) : 0.0F;

        for (int i = 0; i < cps.length; i++) {
            String glyph = new String(Character.toChars(cps[i]));
            float w = font.width(glyph) * scale;
            int offset = (roll != null && i < roll.swayPx().length) ? roll.swayPx()[i] : 0;
            float angle = (roll != null && i < roll.angleRad().length) ? roll.angleRad()[i] : 0.0F;
            float x = centerX + offset - w / 2.0F;
            // 钳进屏幕：右列 +4px 位移在极端组合（大字号 + 最大右摆 + 最宽字形）下会越界 1px
            x = Math.max(screenLeft, Math.min(screenRight - w, x));
            float y = top + i * step;
            gui.pose().pushMatrix();
            gui.pose().translate(x, y);
            if (angle != 0.0F) {
                // 绕**自身中心**旋转：先把原点挪到字格中心，转完再挪回来
                gui.pose().translate(w / 2.0F, glyphH / 2.0F);
                gui.pose().rotate(angle);
                gui.pose().translate(-w / 2.0F, -glyphH / 2.0F);
            }
            gui.pose().scale(scale, scale);
            int color = (colors != null && i < colors.length) ? colors[i] : COLOR_DEFAULT;
            gui.text(font, glyph, 0, 0, color, TEXT_SHADOW);
            gui.pose().popMatrix();
        }
    }

    /** 向下吸附到 {@code 1/SCALE_STEPS} 的整数倍（0.25 粒度），保证像素字不被分数缩放糊掉。 */
    private static float snapDown(float scale) {
        int steps = (int) Math.floor(scale * SCALE_STEPS);
        return steps / (float) SCALE_STEPS;
    }
}
