package com.alonie.brbe.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 查询界面对象的统一排序（唯一排序模块：viewer 配方按钮与燃料网格共用）。
 *
 * <p>排序是<b>后端分组</b>：组 = {pin, 普通}——pin 组整体在普通组之前；每个组
 * 内部再按 {@code kindRank} 排（值小 = 靠前）：配方对象 = 可合成 → 残缺 →
 * 不可合成，燃料网格 = 拥有 → 缺失。两组在用户视角<b>直接接在一起</b>（一个
 * 连续列表，无视觉分隔），组间只由"pin 在前"约束，种类顺序绝不跨组比较。
 * 稳定排序：同组同种类保持原顺序（如燃料的燃烧时长升序、JEI 收集顺序）。
 *
 * <p>纯函数、无状态：类别/状态判定（isPinned、kindRank）由调用方按<b>当前</b>
 * 状态提供，调用方负责脏检测触发（pin 版本 / 检索空间哈希）——本模块只负责
 * 排序规则本身。
 *
 * <p><b>不修改入参</b>：始终返回一个新的排序列表——调用方的列表可能是
 * 不可变的（{@code List.of(...)}、{@code stream().toList()}，燃料类别
 * 的网格数据即如此），原地 {@code sort} 会抛
 * {@link UnsupportedOperationException}。
 */
public final class ViewerObjectOrder {

    private ViewerObjectOrder() {
    }

    /**
     * 返回 {@code items} 的排序副本：pin 组（组内按 kindRank）→ 普通组（组内按
     * kindRank）；同组同种类稳定。原列表不被修改。
     */
    public static <T> List<T> reorder(List<T> items,
                                      Predicate<T> isPinned,
                                      ToIntFunction<T> kindRank) {
        List<T> out = new ArrayList<>(items);
        if (out.size() <= 1) return out;
        out.sort(Comparator
                .comparingInt((T t) -> isPinned.test(t) ? 0 : 1)
                .thenComparingInt(kindRank));
        return out;
    }
}
