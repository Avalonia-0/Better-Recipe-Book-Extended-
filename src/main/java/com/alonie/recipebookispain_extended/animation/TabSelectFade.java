package com.alonie.recipebookispain_extended.animation;

/**
 * RBIP 标签「选中 / 取消选中」的渐变过渡（挂在「翻页动画」总开关下）。
 *
 * <p>选中态在原版里是一次性的硬切换：贴图换成选中版、整块（含图标）左移 2px。
 * 这里把它换成<b>交叉渐变</b>——两张贴图在同一个（按插值左移的）位置上各按
 * {@code blend} / {@code 1-blend} 的透明度叠画，图标只画一次、偏移同样按插值走，
 * 所以既没有 2px 的重影，也不需要任何位移动画。</p>
 *
 * <p>曲线沿用翻页动画那条指数减速（{@code rate = 6.2 / 时长}），时长取配置
 * 「动画时长」的一半 —— 选中反馈要比翻页更跟手（默认 0.5s → 0.25s）。</p>
 */
public final class TabSelectFade {

    /** 与配方区/标签栏翻页动画同一系数：rate = 6.2 / 时长（秒）。 */
    public static final float BASE_RATE = 6.2F;
    /** 渐变时长相对配置「动画时长」的倍率（选中反馈比翻页快一倍）。 */
    public static final float DURATION_FACTOR = 0.5F;
    /** 时长下限，避免把「动画时长」调到极小时渐变變成硬切。 */
    public static final float MIN_DURATION = 0.12F;
    /** 单帧位移比例上限（与翻页动画同一公式）。 */
    public static final float CAP_BASE = 0.45F;
    public static final float CAP_SCALE = 0.12F;
    /** 收敛阈值：差值小于它就直接吸附到端点，渐变结束。 */
    public static final float SNAP = 0.002F;
    /** 原版选中态把标签（含图标）左移的像素数。 */
    public static final int SELECTED_SHIFT = 2;

    /**
     * 「停靠渐显」的窗口宽度（像素）：登场标签离静止位还剩这么多像素时，就把**压在书皮上的
     * 那一条**渐显出来；走到静止位时正好渐显满。
     *
     * <p>这样"缓慢停靠"和"渐变过渡"是同一段动作（用户 2026-10-04 提议）——渐显量直接跟着
     * 剩余位移走，不需要任何计时器：位移本身是指数减速的，所以时间上天然是 ease-out，
     * 而且绝不可能出现"停下来之后再淡一下"的割裂感。</p>
     */
    public static final int DOCK_REVEAL_DISTANCE = 12;

    /**
     * 停靠渐显的强度：{@code remainingPx ∈ [0, DOCK_REVEAL_DISTANCE]} → {@code [0,1]}。
     *
     * <p>只剩 0 像素（已在静止位）= 1（完全显形）；离静止位还有整段窗口 = 0（完全被书皮盖住）。
     * 渐显只作用于**越过书皮边缘的那一块**（正常朝向就是右端那几列），本体与图标不受影响。</p>
     */
    public static float dockReveal(int remainingPx) {
        if (remainingPx >= DOCK_REVEAL_DISTANCE) {
            return 0.0F;
        }
        float x = Math.max(0, remainingPx) / (float) DOCK_REVEAL_DISTANCE;
        return 1.0F - x;
    }

    /** 点击切换时那条指数曲线的速率（1/秒，时长取配置的一半 → 比翻页更跟手）。 */
    public static float rate(float pageAnimationDuration) {
        return BASE_RATE / Math.max(MIN_DURATION, pageAnimationDuration * DURATION_FACTOR);
    }

    /** 当前该左移多少像素（0 = 未选中外观，{@link #SELECTED_SHIFT} = 完全选中外观）。 */
    public static int shift(float blend) {
        return Math.round(SELECTED_SHIFT * blend);
    }

    /** 把 0–1 的渐变系数转成贴图着色的 ARGB（只改 alpha，不染色）。 */
    public static int color(float alpha) {
        int a = Math.round(alpha * 255.0F);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return a << 24 | 0xFFFFFF;
    }

    /** 朝目标推进一步（{@code deltaTicks} = 部分刻，与配方区动画同一条指数曲线）。 */
    public static float advance(float blend, float target, float rate, float deltaTicks) {
        float diff = target - blend;
        if (Math.abs(diff) < SNAP) {
            return target;
        }
        float step = 1.0F - (float) Math.exp(-rate * (deltaTicks / 20.0F));
        float cap = CAP_BASE + (float) Math.sqrt(Math.abs(diff)) * CAP_SCALE;
        float move = Math.min(Math.min(Math.abs(diff) * step, cap), Math.abs(diff));
        return blend + Math.signum(diff) * move;
    }
}
