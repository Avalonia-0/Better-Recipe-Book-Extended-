package com.alonie.brbe.util;

import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * 「同产物同形融合」条目的成员表（用户 2026-09-29 定）。
 *
 * <p>融合条目展示的是逐槽选项的**并集**（{@code SlotDisplay.Composite} 轮循差异部分），但服务端是按
 * {@link RecipeDisplayId} 找**真实配方**的：只把主成员的 id 发过去，玩家手上只有另一版材料时就会
 * 放不出来。因此管线每次运行都把"融合条目 → 成员条目"登记在这里，点击时由
 * {@code mixins/pipeline/RecipeBookComponentMixin} 换成本次物品栏真能做的那一条再发包。</p>
 *
 * <p>pin 状态同理需要"跟随"：pin 键是 {@link PinnableRecipeCollection#idFor}（display 的散列），
 * 融合条目的键与成员都不同 —— 所以这里额外登记成员的 pin 键，供
 * {@code PinnedRecipeManager} 判断"融合条目是否等效于被 pin"以及把 pin 落到成员键上。</p>
 *
 * <p>生命周期 = 最近一次管线运行（{@link #beginRun()} 在每个 Stage 2.6 开头清空）；管线输出命中
 * 缓存时不重跑，此时沿用上一次的登记即可（同一份输入 → 同一份登记）。</p>
 */
public final class FusedRecipeVariants {

    /** 融合条目 id → 成员条目（放置时挑"真能做的那一条"）。 */
    private static final java.util.Map<RecipeDisplayId, java.util.List<RecipeDisplayEntry>> BY_ID =
            new java.util.HashMap<>();

    /** 融合条目的 pin 键 → 成员的 pin 键（pin 跟随 / 落键）。 */
    private static final java.util.Map<Identifier, java.util.List<Identifier>> MEMBER_PINS =
            new java.util.HashMap<>();

    private FusedRecipeVariants() {
    }

    public static void beginRun() {
        BY_ID.clear();
        MEMBER_PINS.clear();
    }

    public static void register(RecipeDisplayEntry fused, java.util.List<RecipeDisplayEntry> members) {
        if (fused == null || fused.id() == null || members == null || members.size() < 2) {
            return;
        }
        java.util.List<RecipeDisplayEntry> entries = new java.util.ArrayList<>(members);
        java.util.List<Identifier> pins = new java.util.ArrayList<>(members.size());
        for (RecipeDisplayEntry member : members) {
            pins.add(PinnableRecipeCollection.idFor(member));
        }
        BY_ID.put(fused.id(), entries);
        MEMBER_PINS.put(PinnableRecipeCollection.idFor(fused), pins);
    }

    /** 融合条目（按 pin 键识别）对应的成员 pin 键；不是融合条目 → {@code null}。 */
    public static java.util.List<Identifier> memberPins(Identifier fusedPinKey) {
        return fusedPinKey == null ? null : MEMBER_PINS.get(fusedPinKey);
    }

    /**
     * 该 id 实际要放置的版本：融合条目 → 成员里**当前物品栏真能做**的第一条；否则原样返回。
     * （成员的 {@code canCraft} 用的就是各自真实的 requirements，与原版判定同源。）
     */
    public static RecipeDisplayId bestVariant(RecipeDisplayId id, StackedItemContents stacked) {
        if (id == null) {
            return null;
        }
        java.util.List<RecipeDisplayEntry> members = BY_ID.get(id);
        if (members == null || stacked == null) {
            return id;
        }
        for (RecipeDisplayEntry member : members) {
            if (member.id() != null && member.canCraft(stacked)) {
                return member.id();
            }
        }
        return id;
    }
}
