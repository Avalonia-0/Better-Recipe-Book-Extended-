package com.alonie.brbe.generic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.Pinnable;
import com.alonie.brbe.util.RecipeSlotState;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * 自研配方书（酿造台 / 锻造台）的一个集合（= 页面上的一格按钮）。
 *
 * <h3>槽位状态缓存</h3>
 * 「哪些配方材料齐备 / 哪些残缺」只取决于槽位（与鼠标上的物品）。同一帧里这些结论会被
 * 反复询问——{@link GenericRecipeButton} 每帧问 5～6 次、排序原因剥离管线每格问 3 次、
 * 替代配方组浮层再问几次——而每次询问原本都要重扫「集合内全部配方 × 全部容器槽位」。
 * 锻造台纹饰组一个集合 29 条配方、18 个组时，"打开配方书"因此要做几十万次槽位扫描。
 *
 * <p>现在这些结论按 {@link RecipeSlotState} 的槽位版本缓存：槽位没变时全部 O(1)，
 * 槽位一变（拾取/放置/切换物品）整体重算一次。子类只需给出两条**原始判据**
 * （{@link #hasMaterials} / {@link #hasPartialMaterials}），其余全部由本类缓存派生，
 * 判据语义与缓存引入前逐条一致。</p>
 */
public abstract class GenericRecipeBookCollection<R extends GenericRecipe, M extends AbstractContainerMenu> implements Pinnable {
    protected final RegistryAccess registryAccess;
    protected List<R> recipes;
    protected M menu;

    protected GenericRecipeBookCollection(List<? extends R> list, M menu, RegistryAccess registryAccess) {
        this.menu = menu;
        this.recipes = ImmutableList.copyOf(list);
        this.registryAccess = registryAccess;
    }

    public List<R> getRecipes() {
        return recipes;
    }

    // ── 槽位状态缓存 ──────────────────────────────────────────────────────────

    private long brbe$stateVersion = Long.MIN_VALUE;
    private List<R> brbe$craftableRecipes = List.of();
    private List<R> brbe$otherRecipes = List.of();
    private List<R> brbe$partialRecipes = List.of();
    private Set<R> brbe$craftableSet = Set.of();
    private Set<Identifier> brbe$partialIds = Set.of();
    private boolean brbe$anyCraftable;
    private boolean brbe$anyPartial;

    /** 原始判据：这一条配方材料是否齐备。 */
    protected abstract boolean hasMaterials(R recipe, NonNullList<Slot> slots);

    /** 原始判据：这一条配方是否残缺（缺料但已持有一部分）。只会在 {@link #hasMaterials} 为 false 时被询问。 */
    protected abstract boolean hasPartialMaterials(R recipe, NonNullList<Slot> slots);

    /** 缓存过期则整体重算一次（槽位版本未变时立即返回）。 */
    private void brbe$ensureState() {
        long version = RecipeSlotState.version(this.menu);
        if (version == this.brbe$stateVersion) {
            return;
        }

        NonNullList<Slot> slots = this.menu.slots;
        List<R> craftable = new ArrayList<>(this.recipes.size());
        List<R> other = new ArrayList<>(this.recipes.size());
        List<R> partial = new ArrayList<>();
        Set<R> craftableSet = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Identifier> partialIds = new HashSet<>();

        for (R recipe : this.recipes) {
            if (this.hasMaterials(recipe, slots)) {
                craftable.add(recipe);
                craftableSet.add(recipe);
            } else {
                other.add(recipe);
                if (this.hasPartialMaterials(recipe, slots)) {
                    partial.add(recipe);
                    Identifier id = recipe.id();
                    if (id != null) {
                        partialIds.add(id);
                    }
                }
            }
        }

        this.brbe$craftableRecipes = List.copyOf(craftable);
        this.brbe$otherRecipes = List.copyOf(other);
        this.brbe$partialRecipes = List.copyOf(partial);
        this.brbe$craftableSet = craftableSet;
        this.brbe$partialIds = Set.copyOf(partialIds);
        this.brbe$anyCraftable = !craftable.isEmpty();
        this.brbe$anyPartial = !partial.isEmpty();
        this.brbe$stateVersion = version;
    }

    /** 未走缓存（调用方传进来的不是本集合所属菜单的槽位）时的兜底计算。 */
    private List<R> brbe$computePartially(NonNullList<Slot> slots) {
        List<R> partial = new ArrayList<>();
        for (R recipe : this.recipes) {
            if (!this.hasMaterials(recipe, slots) && this.hasPartialMaterials(recipe, slots)) {
                partial.add(recipe);
            }
        }
        return partial;
    }

    public boolean has(Identifier Identifier) {
        for (R recipe : getRecipes()) {
            if (recipe.id().equals(Identifier)) {
                return true;
            }
        }

        return false;
    }

    public R getFirst() {
        return this.getRecipes().get(0);
    }

    /**
     * 材料齐备（true）/ 其余（false）两个子列表。**返回只读列表**（缓存本体），
     * 调用方需要可变列表时自行复制（见 {@link GenericRecipeButton#getOrderedRecipes()}）。
     */
    public final List<R> getDisplayRecipes(boolean craftable) {
        this.brbe$ensureState();
        return craftable ? this.brbe$craftableRecipes : this.brbe$otherRecipes;
    }

    protected final boolean atleastOneCraftable(NonNullList<Slot> slots) {
        if (slots != this.menu.slots) {
            for (R recipe : this.recipes) {
                if (this.hasMaterials(recipe, slots)) {
                    return true;
                }
            }
            return false;
        }
        this.brbe$ensureState();
        return this.brbe$anyCraftable;
    }

    public final boolean isCraftable(R recipe, NonNullList<Slot> slots) {
        if (slots != this.menu.slots) {
            return this.hasMaterials(recipe, slots);
        }
        this.brbe$ensureState();
        return this.brbe$craftableSet.contains(recipe);
    }

    /**
     * 按 **id** 判定残缺 —— 与既有调用方（单元格红罩、排序原因剥离、组浮层）语义一致：
     * 同一 id 的变体（例如同一瓶药水的两条酿造路线）视为同一配方。
     *
     * <p><b>不受</b> {@code partialMarkingEnabled} 影响（排序原因剥离用它分类，残缺点
     * 关闭时仍要参与排序）。要"是否显示残缺标记"请用 {@link #isPartiallyMarked}。</p>
     */
    public final boolean isPartiallyCraftable(R recipe) {
        if (recipe == null) {
            return false;
        }
        this.brbe$ensureState();
        Identifier id = recipe.id();
        return id != null && this.brbe$partialIds.contains(id);
    }

    /**
     * 渲染语义的"残缺"判定：与无参 {@link #getPartiallyCraftableRecipes()} 同义
     * （配置项 {@code partialMarkingEnabled} 关闭时恒 false），但走缓存、O(1)。
     */
    public final boolean isPartiallyMarked(R recipe) {
        if (!BetterRecipeBook.config.partialMarkingEnabled) {
            return false;
        }
        return this.isPartiallyCraftable(recipe);
    }

    protected final boolean atleastOnePartiallyCraftable(NonNullList<Slot> slots) {
        if (slots != this.menu.slots) {
            return !this.brbe$computePartially(slots).isEmpty();
        }
        this.brbe$ensureState();
        return this.brbe$anyPartial;
    }

    public final List<R> getPartiallyCraftableRecipes(NonNullList<Slot> slots) {
        if (slots != this.menu.slots) {
            return this.brbe$computePartially(slots);
        }
        this.brbe$ensureState();
        return this.brbe$partialRecipes;
    }

    /**
     * Returns partially-craftable recipes in this collection, or empty if
     * the feature is disabled in config.  All renderers should call this
     * no-arg variant rather than the slots variant directly.
     */
    public List<R> getPartiallyCraftableRecipes() {
        if (!BetterRecipeBook.config.partialMarkingEnabled) {
            return Lists.newArrayList();
        }
        return this.getPartiallyCraftableRecipes(this.menu.slots);
    }

    /**
     * 用**子集**重建一个同源集合（排序原因剥离 stage 用，用户 2026-09-27 诉求）：
     * 子组与"重打包的原组"都由它生成。子类返回自身具体类型（协变覆写）。
     *
     * <p>⚠️ 实现里必须调 {@link #brbe$inheritExtractionState(GenericRecipeBookCollection)}
     * 继承"剥离子组"标记（子组的子集仍是子组）。</p>
     */
    public abstract GenericRecipeBookCollection<R, M> subset(List<R> subset);

    // ── 排序原因剥离的产物标记 ───────────────────────────────────────────────

    /**
     * 本集合是不是**排序原因剥离出来的子组**（{@link com.alonie.brbe.util.RecipeExtraction}：pin /
     * 可合成 / 残缺 / 搜索命中的变体从原组剥出来单独成格）。
     *
     * <p>语义：<b>折叠展示（锻造台纹饰组的模板、升级组的锭）只属于原组那一格</b>——
     * 重打包的原组仍是原组（false），只有真正剥离出来的子组是 true。子格是"我挑出来的那几条"，
     * 必须画自己那几条配方的产物（用户 2026-09-27 三次反馈：从纹饰组 pin 出来的一条
     * 画的是纹饰模板，看不出 pin 的是哪件装备）。</p>
     */
    private boolean brbe$extractionSubgroup;

    /** 见 {@link #brbe$extractionSubgroup}。 */
    public final boolean isExtractionSubgroup() {
        return this.brbe$extractionSubgroup;
    }

    /** 把本集合标记为剥离子组（由 {@link com.alonie.brbe.util.RecipeExtraction.Plan#onSubgroupPack} 调用）。 */
    public final void markExtractionSubgroup() {
        this.brbe$extractionSubgroup = true;
    }

    /**
     * {@link #subset(List)} 实现里的公共一步：继承父集合的剥离标记
     * （子组的子集仍是子组；重打包的原组仍是原组）。
     */
    protected final void brbe$inheritExtractionState(GenericRecipeBookCollection<R, M> parent) {
        this.brbe$extractionSubgroup = parent.brbe$extractionSubgroup;
    }
}
