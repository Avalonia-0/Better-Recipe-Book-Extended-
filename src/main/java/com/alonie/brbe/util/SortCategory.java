package com.alonie.brbe.util;

/**
 * 单个**配方变体**的排序类别（用户 2026-09-27 诉求：替代配方组内"需要调整排序"的变体
 * 从原组剥离出来、按类别各自成组，而不是像原版那样"拖家带口"整组前移）。
 *
 * <p>优先级从低到高 = {@link #NORMAL} < {@link #PARTIAL} < {@link #CRAFTABLE} < {@link #PINNED}：
 * 剥离时以**组内最低类别为基线**（{@code ANCHOR = MIN}，见
 * {@link RecipeExtraction#extract}），比基线高的变体每个类别各成一个子组，原组保留基线变体
 * 且位置不变；子组再参与正常排序（可合成的进"可合成"区、残缺进"残缺"区、pin 仍按既有
 * 规则置顶）。</p>
 *
 * <p>新增排序原因时：在这里加一个枚举值、在 {@link RecipeExtraction} 的调用方给出该变体的
 * 类别即可，剥离/成组/多层嵌套都是通用逻辑。</p>
 */
public enum SortCategory {

    /** 默认：既不可合成也不残缺、未 pin —— 留在原组。 */
    NORMAL(0),

    /** 残缺配方（材料只占一部分）。 */
    PARTIAL(1),

    /** 完整可合成。 */
    CRAFTABLE(2),

    /** 已固定（pin）—— 绝对优先级，子组置顶（既有规则）。 */
    PINNED(3);

    private final int priority;

    SortCategory(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return this.priority;
    }

    /** 优先级更高者（更"需要前移"）。 */
    public boolean isHigherThan(SortCategory other) {
        return this.priority > other.priority;
    }

    /** 由低到高的枚举顺序（剥离成组时按此顺序遍历，便于稳定输出）。 */
    public static final SortCategory[] ASCENDING = {NORMAL, PARTIAL, CRAFTABLE, PINNED};
}
