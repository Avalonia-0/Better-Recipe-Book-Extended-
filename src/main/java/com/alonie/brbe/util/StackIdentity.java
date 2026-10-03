package com.alonie.brbe.util;

import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * 「栈级身份」：**物品 + 组件补丁**。
 *
 * <p>背景（用户 2026-10-02 反馈）：LEI（查询窗口）此前只按**物品注册 id**索引与匹配，
 * 而大量数据包/模组物品用的是**原版物品 id + 组件**（Guns++ 的 44 把枪全是
 * {@code minecraft:carrot_on_a_stick} + 各自的 {@code minecraft:item_model} /
 * {@code custom_data}）→ 查一把枪等于查全部枪、窗口恢复时退化成裸物品。</p>
 *
 * <p>本类给出三层键，供 {@code RecipeViewerEngine} 做**分层匹配**（严格→宽松，避免
 * "精确到查不到"）：</p>
 * <ol>
 *   <li>{@link IdentityKey}：物品 + **剔除易变组件后**的补丁 —— 主匹配级。玩家手里的枪
 *       带着"打了几发"的 {@code custom_data}、耐久等运行时数据，和配方产物并不完全相等，
 *       所以这些组件不参与身份判定（见 {@link #VOLATILE}）。</li>
 *   <li>{@link ModelKey}：物品 + {@code minecraft:item_model} —— 数据包的自定义外观通常
 *       就写在模型路径上（{@code minecraft:guns/ak_47}），是很好的第二把钥匙。</li>
 *   <li>物品 id —— 引擎里保留的旧索引，作为最后兜底（行为与本类引入前一致）。</li>
 * </ol>
 *
 * <p>另提供补丁的 SNBT 编解码，供 {@code queryviewers.json} 持久化栈身份
 * （{@code ViewSpec.components} 字段；编解码需要注册表上下文，取不到时返回
 * {@code null} / 不改动，调用方自然退化到物品级）。</p>
 */
public final class StackIdentity {

    /**
     * **运行时会被改写**的组件 —— 算身份键时剔除。
     *
     * <p>取舍标准：这些组件的值在"配方产物"和"玩家手里的成品"之间一定不同，
     * 或者与"这是哪个物品"无关；把它们算进身份会导致精确匹配永远 miss。</p>
     */
    private static final Set<DataComponentType<?>> VOLATILE = Set.of(
            DataComponents.CUSTOM_DATA,     // 数据包私有数据：Guns++ 的 gz_data.bullets/reload_time 随手枪状态变化
            DataComponents.DAMAGE,          // 耐久：用过的工具/武器与配方产物不同
            DataComponents.LORE,            // 数据包常用 item modifier 动态改写（子弹数/装填时间那类文案）
            DataComponents.REPAIR_COST);    // 铁砧累计惩罚，纯运行时状态

    private StackIdentity() {}

    // ================================================================
    //  索引键
    // ================================================================

    /** 身份键：物品 + 剔除易变组件后的补丁（分层匹配的第 ① 级）。 */
    public record IdentityKey(Item item, DataComponentPatch patch) {
        public static IdentityKey of(ItemStack stack) {
            return stack == null || stack.isEmpty()
                    ? null : new IdentityKey(stack.getItem(), identityPatch(stack));
        }
    }

    /** 模型键：物品 + {@code minecraft:item_model}（分层匹配的第 ② 级）；无该组件时为 null。 */
    public record ModelKey(Item item, Identifier model) {
        public static ModelKey of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return null;
            Identifier model = stack.get(DataComponents.ITEM_MODEL);
            return model == null ? null : new ModelKey(stack.getItem(), model);
        }
    }

    /** 剔除 {@link #VOLATILE} 后的组件补丁；没有剩余组件时返回 {@link DataComponentPatch#EMPTY}。
     *
     *  <p>用官方的 {@link DataComponentPatch#forget(java.util.function.Predicate)}
     *  （26.3 的 {@code DataComponentPatch} **没有**公开 {@code entrySet}，手撸遍历拿不到条目），
     *  它同时保留"显式删除组件"的语义。</p> */
    public static DataComponentPatch identityPatch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return DataComponentPatch.EMPTY;
        DataComponentPatch full = stack.getComponentsPatch();
        if (full.isEmpty()) return DataComponentPatch.EMPTY;
        return full.forget(VOLATILE::contains);
    }

    // ================================================================
    //  持久化（queryviewers.json）
    // ================================================================

    /** 把栈的组件补丁编码成 SNBT（写入 {@code ViewSpec.components}）；不可用时返回 null。 */
    public static String encodePatch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) return null;
        HolderLookup.Provider registries = registries();
        if (registries == null) return null;
        try {
            DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            return DataComponentPatch.CODEC.encodeStart(ops, patch)
                    .result().map(Object::toString).orElse(null);
        } catch (Throwable t) {
            BrbeLogger.log("BRBE-STACKID", "encode patch failed: {}", t.toString());
            return null;
        }
    }

    /**
     * 把 {@link #encodePatch} 写下的 SNBT 应用回一个（只有物品 id 的）栈。
     *
     * @return 成功应用（或本就没有组件）返回 true；解析/解码失败返回 false（调用方退化为物品级）
     */
    public static boolean applyEncodedPatch(ItemStack stack, String snbt) {
        if (stack == null || stack.isEmpty() || snbt == null || snbt.isEmpty()) return false;
        HolderLookup.Provider registries = registries();
        if (registries == null) return false;
        try {
            DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            Tag tag = TagParser.parseCompoundFully(snbt);
            DataComponentPatch patch = DataComponentPatch.CODEC.parse(ops, tag)
                    .result().orElse(null);
            if (patch == null) return false;
            stack.applyComponents(patch);
            return true;
        } catch (Throwable t) {
            BrbeLogger.log("BRBE-STACKID", "decode patch failed: {}", t.toString());
            return false;
        }
    }

    /** 客户端当前存档的注册表上下文；标题界面 / 未进世界时为 null。 */
    private static HolderLookup.Provider registries() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
        } catch (Throwable t) {
            return null;
        }
    }
}
