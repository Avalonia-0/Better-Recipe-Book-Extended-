package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 配方状态诊断工具 — 每次物品栏刷新后检查配方状态一致性（1.21.1 适配版）。
 *
 * <p><b>调查 1（独立材料预测）</b>：不信任 BRBE 的 partial 标记，
 * 直接从配方成分（旧式 {@code ShapedRecipe}/{@code ShapelessRecipe} 的
 * {@code getIngredients()}）+ 库存独立推测配方状态。</p>
 *
 * <p><b>调查 2（纹理渲染检查）</b>：读取 BRBE 实际标记状态
 * （craftable set + partial tag）。</p>
 *
 * <p><b>合格性判定</b>（预测状态必须与 BRBE 标记一致）：</p>
 * <table>
 *   <tr><td>预测 CRAFTABLE</td><td>→ 必须 在 craftable set 且 非 partial</td><td>否-可合成配方</td></tr>
 *   <tr><td>预测 PARTIAL</td><td>→ 必须 标记为 partial</td><td>是-缺少部分材料的配方</td></tr>
 *   <tr><td>预测 UNCRAFTABLE</td><td>→ 必须 不在 craftable set 且 非 partial</td><td>是-不可合成配方</td></tr>
 *   <tr><td>预测 UNKNOWN</td><td>→ 跳过（不判合格/不合格）</td><td>无法推测</td></tr>
 * </table>
 *
 * <p>3×3 配方（{@code needsLargerGrid}）永不标 partial——材料齐全与否都不判
 * "缺少部分材料"，由 {@code IncompatibleCraftingUtil} 的网格警告处理。</p>
 */
public final class RecipeStateDiagnostic {

    private static final Logger LOG = LogManager.getLogger("brbe-diag");
    private static long lastDiagnosticSlotHash;

    private RecipeStateDiagnostic() {}

    /** 独立预测的状态 */
    enum PredictedState { CRAFTABLE, PARTIAL, UNCRAFTABLE, UNKNOWN }

    /**
     * @param processedCollections 管道处理后的 RecipeCollection 列表
     * @param menuSlots            当前容器槽位
     */
    public static void run(List<RecipeCollection> processedCollections,
                           NonNullList<Slot> menuSlots) {
        if (processedCollections == null || processedCollections.isEmpty()) return;

        long hash = PartialCraftingUtil.slotHash(menuSlots);
        if (hash == lastDiagnosticSlotHash) return;
        lastDiagnosticSlotHash = hash;

        Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(menuSlots);
        // Item → 总数量。诊断的数量检查用它判断"类型齐全但数量不足"的配方。
        Map<Item, Integer> inventoryCounts = new HashMap<>();
        for (Slot slot : menuSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        LOG.warn("══════════════════════════════════════════════");
        LOG.warn("[BRBE-DIAG] 物品栏刷新 — 配方状态合格性检测开始");
        LOG.warn("[BRBE-DIAG] slotHash=0x{} 集合数={} 库存物品={}",
                Long.toHexString(hash), processedCollections.size(), inventoryCounts.size());

        int total = 0, valid = 0, invalid = 0, skipped = 0;
        Map<String, List<String>> invalidByCollection = new LinkedHashMap<>();

        for (RecipeCollection collection : processedCollections) {
            RecipeCollectionAccessor acc = (RecipeCollectionAccessor) collection;
            Set<RecipeHolder<?>> craftableSet = acc.brbe$getCraftable();

            for (RecipeHolder<?> holder : collection.getRecipes()) {
                ResourceLocation id = holder.id();
                total++;

                // ── 调查 1：独立材料预测 ──
                PredictedState predicted = predictState(holder, inventoryItems, inventoryCounts);
                if (predicted == PredictedState.UNKNOWN) {
                    skipped++;
                    continue;
                }

                // ── 调查 2：BRBE 实际标记 ──
                boolean inCraftable = craftableSet.contains(holder);
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, id);
                boolean needsGrid = PartialCraftingUtil.needsLargerGrid(holder);

                // ── 合格性判定 ──
                String label;
                boolean ok;

                if (needsGrid) {
                    // 3×3 配方：永不标 partial。材料齐全 → 不可合成轮廓 + 网格警告（合格）
                    ok = !isPartial;
                    label = isPartial
                            ? "不合格(3×3配方被误标partial)"
                            : "3×3配方(网格决定, 材料" + (predicted == PredictedState.CRAFTABLE ? "齐全" : "不足") + ")";
                } else {
                    // 2×2 网格可容纳配方：预测必须与标记一致
                    switch (predicted) {
                        case CRAFTABLE -> {
                            ok = inCraftable && !isPartial;
                            label = "否-可合成配方";
                        }
                        case PARTIAL -> {
                            ok = isPartial;
                            label = "是-缺少部分材料的配方";
                        }
                        case UNCRAFTABLE -> {
                            ok = !inCraftable && !isPartial;
                            label = "是-不可合成配方";
                        }
                        default -> { continue; }
                    }
                }

                if (ok) {
                    valid++;
                } else {
                    invalid++;
                    String name = getDisplayName(holder);
                    invalidByCollection.computeIfAbsent(name + " [" + id + "]", k -> new ArrayList<>())
                            .add(String.format("  预测=%s | 实际=cft:%s partial:%s | %s",
                                    predicted, inCraftable ? "Y" : "N",
                                    isPartial ? "Y" : "N", label));
                }
            }
        }

        LOG.warn("[BRBE-DIAG] 检测完成: 总计={} 合格={} 不合格={} 无法推测={}",
                total, valid, invalid, skipped);

        if (!invalidByCollection.isEmpty()) {
            LOG.warn("[BRBE-DIAG] ── 不合格配方详情 ──");
            for (var e : invalidByCollection.entrySet()) {
                LOG.warn("[BRBE-DIAG] {}", e.getKey());
                e.getValue().forEach(line -> LOG.warn("[BRBE-DIAG]   {}", line));
            }
        }

        LOG.warn("[BRBE-DIAG] 库存: {}", snapshot(menuSlots));
        LOG.warn("══════════════════════════════════════════════");
    }

    // ═══════════ 调查 1：独立材料预测（旧式 Recipe 模型） ═══════════

    private static PredictedState predictState(RecipeHolder<?> holder, Set<Item> inventoryItems,
                                               Map<Item, Integer> inventoryCounts) {
        Recipe<?> recipe = holder.value();
        NonNullList<Ingredient> ingredients;
        if (recipe instanceof ShapedRecipe shaped) {
            ingredients = shaped.getIngredients();
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            ingredients = shapeless.getIngredients();
        } else {
            return PredictedState.UNKNOWN; // 熔炉/锻造/切石机等无法用此方式推测
        }

        // 统计每个物品在配方所有成分槽中出现的次数。形状配方里同一物品
        // 占多个槽 = 需要多份（如 2×羊毛地毯）。数量不足的配方应预测为
        // PARTIAL，而不是 CRAFTABLE。
        Map<Item, Integer> neededCounts = new HashMap<>();
        int totalSlots = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            totalSlots++;
            // 成分的候选物品：取第一个在库存中存在的（类型匹配）
            Item chosen = null;
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty() && inventoryItems.contains(stack.getItem())) {
                    chosen = stack.getItem();
                    break;
                }
            }
            if (chosen != null) {
                neededCounts.merge(chosen, 1, Integer::sum);
            }
        }

        if (totalSlots == 0) return PredictedState.UNKNOWN;

        // 数量不足：某物品在成分槽出现次数 > 库存数量
        for (Map.Entry<Item, Integer> e : neededCounts.entrySet()) {
            int available = inventoryCounts.getOrDefault(e.getKey(), 0);
            if (available < e.getValue()) {
                return PredictedState.PARTIAL;
            }
        }

        int matchedSlots = neededCounts.size();
        if (matchedSlots == totalSlots) return PredictedState.CRAFTABLE;
        if (matchedSlots == 0) return PredictedState.UNCRAFTABLE;
        return PredictedState.PARTIAL;
    }

    // ═══════════ 工具方法 ═══════════

    private static String snapshot(NonNullList<Slot> slots) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Slot s : slots) {
            ItemStack st = s.getItem();
            if (!st.isEmpty())
                m.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        m.forEach((id, c) -> sb.append(id).append("×").append(c).append("  "));
        return sb.toString();
    }

    private static String getDisplayName(RecipeHolder<?> holder) {
        try {
            ItemStack result = holder.value().getResultItem(null);
            if (result != null && !result.isEmpty()) return result.getHoverName().getString();
        } catch (Exception ignored) {}
        return holder.id().toString();
    }
}
