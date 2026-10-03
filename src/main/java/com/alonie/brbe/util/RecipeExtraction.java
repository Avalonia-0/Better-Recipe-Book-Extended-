package com.alonie.brbe.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * **按排序类别剥离替代配方组的通用核心**（用户 2026-09-27 诉求）。
 *
 * <p>规则（与既有 pin 剥离机制同源，见 2026-08-27 一百零八轮）：</p>
 * <ol>
 *   <li>算出组内每个变体的 {@link SortCategory}；</li>
 *   <li><b>基线 = 组内最低类别</b>（{@link #ANCHOR_IS_MIN}）——基线变体留在原组、位置不变；</li>
 *   <li>比基线高的类别**每个类别各成一个子组**（同类别多个变体合并成 <b>子组</b>，
 *       单个变体则是单配方组——与"1 个 pin 独立成组 / ≥2 个成副本组"一致）；</li>
 *   <li>子组**递归**再剥离（多层继承：可合成子组里被 pin 的变体再剥出孙组）；</li>
 *   <li>搜索时"未命中"的变体视为不可见 → 原组可能被整体丢弃（只留下命中的子组）。</li>
 * </ol>
 *
 * <p>剥离只负责**分组**；排到哪儿由既有排序阶段决定（pin 置顶、可合成 → 残缺 → 其余）。</p>
 *
 * <p><b>为什么基线取最低而不是多数</b>：用户要的是"哪个变体需要调序就剥哪个"——只要组内
 * 存在类别差异，处于高类别的变体就该按自己的类别去排。代价是"4 个可合成 + 1 个不可合成"
 * 也会拆成 子组(4) + 单配方(1)；若以后想改成"多数派留在原组"，把基线换成多数类别即可
 * （只改 {@link #anchorOf}）。</p>
 */
public final class RecipeExtraction {

    private RecipeExtraction() {
    }

    /** 递归深度上限（类别只有 4 档，正常 2~3 层就到底；防病态数据无限套娃）。 */
    public static final int MAX_DEPTH = 6;

    /** 基线规则：{@code true} = 组内**最低**类别留在原组（当前实现）。 */
    public static final boolean ANCHOR_IS_MIN = true;

    /**
     * 一个配方书类型要提供的最小信息（原版 {@code RecipeCollection} 与自研书集合各实现一份）。
     *
     * @param <C> 集合类型
     * @param <E> 变体/条目类型
     */
    public interface Plan<C, E> {

        /** 该集合的全部变体（顺序即展示顺序）。 */
        List<E> entries(C collection);

        /** 变体自己的排序类别。 */
        SortCategory category(C collection, E entry);

        /** 变体本帧是否可见（搜索时未命中 = false；非搜索恒 true）。 */
        boolean visible(C collection, E entry);

        /** 用**子集**重建一个同源集合（子组 / 重打包的原组）。 */
        C subset(C parent, List<E> entries);

        /**
         * 新建的子组/重打包组交给调用方补做"按集合对象身份"的状态（原版：重放残缺标记；
         * 自研书：无需）。默认什么都不做。
         */
        default void onPacksCreated(List<C> packs) {
        }

        /**
         * **剥离子组**（按类别从原组剥出来的那一格）建好后回调；**重打包的原组不走这里**。
         *
         * <p>自研书用它给子组建打上"不是原组"的标记（
         * {@link com.alonie.brbe.generic.GenericRecipeBookCollection#markExtractionSubgroup()}）——
         * 折叠展示（锻造台纹饰组的模板 / 升级组的锭）只属于原组那一格，子格要画自己的产物
         * （用户 2026-09-27 三次反馈）。原版书用它登记"哪些格子是剥离子组"（副本去重用，
         * 2026-09-28 同产物合并）。默认什么都不做。</p>
         *
         * @param parent 被剥离的原集合
         * @param pack   新建的剥离子组
         */
        default void onSubgroupPack(C parent, C pack) {
        }
    }

    /**
     * 剥离整张列表：逐个集合展开成 [原组（基线变体）] + [各子组（递归）]。
     *
     * @return 新列表（未发生剥离的集合原样引用，不复制）
     */
    public static <C, E> List<C> extract(List<C> collections, Plan<C, E> plan) {
        if (collections == null || collections.isEmpty()) {
            return collections;
        }
        List<C> out = new ArrayList<>(collections.size());
        for (C collection : collections) {
            out.addAll(extractOne(collection, plan, 0));
        }
        return out;
    }

    /** 单个集合的剥离（递归）。 */
    private static <C, E> List<C> extractOne(C collection, Plan<C, E> plan, int depth) {
        List<E> all = plan.entries(collection);
        if (all == null || all.size() < 2 || depth >= MAX_DEPTH) {
            return List.of(collection);
        }

        // 1) 可见性：搜索时未命中的变体不参与（原组因此可能变空 → 整组丢弃）
        List<E> visible = new ArrayList<>(all.size());
        for (E entry : all) {
            if (plan.visible(collection, entry)) {
                visible.add(entry);
            }
        }
        if (visible.isEmpty()) {
            return List.of();
        }

        // 2) 基线 = 最低类别；比基线高的按类别分桶
        SortCategory anchor = anchorOf(visible, collection, plan);
        List<E> base = new ArrayList<>();
        Map<SortCategory, List<E>> higher = new LinkedHashMap<>();
        for (E entry : visible) {
            SortCategory category = plan.category(collection, entry);
            if (category == anchor) {
                base.add(entry);
            } else if (category.isHigherThan(anchor)) {
                higher.computeIfAbsent(category, key -> new ArrayList<>()).add(entry);
            } else {
                base.add(entry); // 理论上不可达（anchor 已是最低）；兜底留在原组
            }
        }

        // 3) 无剥离且无隐藏：原组原样返回（不制造多余对象）
        if (higher.isEmpty() && visible.size() == all.size()) {
            return List.of(collection);
        }

        List<C> result = new ArrayList<>(1 + higher.size());
        List<C> newPacks = new ArrayList<>(1 + higher.size());
        // 原组：基线变体；若基线覆盖了全部可见变体且没变化，仍是原对象
        if (!base.isEmpty()) {
            if (base.size() == all.size()) {
                result.add(collection);
            } else {
                C parent = plan.subset(collection, base);
                newPacks.add(parent); // 重打包的原组同样是新对象，也要补状态（如残缺标记）
                result.add(parent);
            }
        }

        // 4) 子组：先建出来 → 交给调用方补状态（残缺标记 / 剥离子组标记）→ 再递归剥离（多层继承）
        List<C> children = new ArrayList<>(higher.size());
        for (SortCategory category : SortCategory.ASCENDING) {
            List<E> group = higher.get(category);
            if (group != null && !group.isEmpty()) {
                C child = plan.subset(collection, group);
                // ⚠️ 必须在下行 onPacksCreated 之前：两处回调的职责不同（本行只标"这是子组"）
                plan.onSubgroupPack(collection, child);
                children.add(child);
            }
        }
        newPacks.addAll(children);
        if (!newPacks.isEmpty()) {
            plan.onPacksCreated(newPacks);
        }
        for (C child : children) {
            result.addAll(extractOne(child, plan, depth + 1));
        }
        return result;
    }

    /** 基线类别：当前 = 最低（见类注释的取舍说明）。 */
    private static <C, E> SortCategory anchorOf(List<E> visible, C collection, Plan<C, E> plan) {
        SortCategory anchor = null;
        for (E entry : visible) {
            SortCategory category = plan.category(collection, entry);
            if (anchor == null || category.priority() < anchor.priority()) {
                anchor = category;
            }
        }
        return anchor == null ? SortCategory.NORMAL : anchor;
    }

    /**
     * 便捷入口：把"新组"收集起来统一交给 {@code remark}（原版书用它重放残缺标记——
     * 新组是全新集合对象，标记按对象身份记录，不重放的话组内残缺配方会退化成不可合成）。
     */
    public static <C, E> Consumer<List<C>> remarkHook(Consumer<List<C>> remark) {
        return remark == null ? packs -> { } : remark;
    }
}
