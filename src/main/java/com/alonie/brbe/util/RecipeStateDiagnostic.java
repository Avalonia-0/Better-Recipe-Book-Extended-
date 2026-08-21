package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 配方状态诊断工具 — 每次物品栏刷新后检查配方状态一致性。
 *
 * <p>两遍调查独立进行，最后对比：</p>
 *
 * <p><b>调查 1（独立材料预测）</b>：不信任 BRBE 的 partial 标记，
 * 直接从配方成分 + 库存独立推测配方状态：
 * <ul>
 *   <li>{@link PredictedState#CRAFTABLE} — 所有成分在库存中都有（材料齐全）</li>
 *   <li>{@link PredictedState#PARTIAL} — 部分成分在库存中</li>
 *   <li>{@link PredictedState#UNCRAFTABLE} — 无任何成分在库存中</li>
 *   <li>{@link PredictedState#UNKNOWN} — 无法从 display 提取成分（熔炉/锻造等）</li>
 * </ul></p>
 *
 * <p><b>调查 2（纹理渲染检查）</b>：读取 BRBE 实际标记状态：
 * <ul>
 *   <li>{@code craftable set} 包含 + 非 partial → 可合成轮廓</li>
 *   <li>{@code partial} 标记 → 可合成轮廓 + 红色覆盖层</li>
 *   <li>两者皆无 → 不可合成轮廓</li>
 * </ul></p>
 *
 * <p><b>合格性判定</b>（预测状态必须与 BRBE 标记一致）：</p>
 * <table>
 *   <tr><td>预测 CRAFTABLE</td><td>→ 必须 在 craftable set 且 非 partial</td><td>否-可合成配方</td></tr>
 *   <tr><td>预测 PARTIAL</td><td>→ 必须 标记为 partial</td><td>是-缺少部分材料的配方</td></tr>
 *   <tr><td>预测 UNCRAFTABLE</td><td>→ 必须 不在 craftable set 且 非 partial</td><td>是-不可合成配方</td></tr>
 *   <tr><td>预测 UNKNOWN</td><td>→ 跳过（不判合格/不合格）</td><td>无法推测</td></tr>
 * </table>
 *
 * <p>这样能捕获「材料齐全却被 BRBE 错误标记为 partial」的 bug
 * （如 3×3 配方铁剑：铁锭+木棍齐全，但 2×2 网格放不下，BRBE 误标 partial）。</p>
 */
public final class RecipeStateDiagnostic {

    private static final Logger LOG = LogManager.getLogger("zzzbrbe-diag");
    private static long lastDiagnosticSlotHash;

    private RecipeStateDiagnostic() {}

    /** 独立预测的状态 */
    enum PredictedState { CRAFTABLE, PARTIAL, UNCRAFTABLE, UNKNOWN }

    /**
     * @param processedCollections 管道输出——与 page.updateCollections 相同的列表
     * @param menuSlots            当前容器槽位
     */
    public static void run(List<RecipeCollection> processedCollections,
                           NonNullList<Slot> menuSlots) {
        run(processedCollections, menuSlots, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public static void run(List<RecipeCollection> processedCollections,
                           NonNullList<Slot> menuSlots, ItemStack carried) {
        if (processedCollections == null || processedCollections.isEmpty()) return;

        long hash = PartialCraftingUtil.slotHash(menuSlots, carried);
        if (hash == lastDiagnosticSlotHash) return;
        lastDiagnosticSlotHash = hash;

        Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(menuSlots, -1, carried);
        // Item → 总数量。诊断的数量检查用它判断"类型齐全但数量不足"的配方。
        Map<Item, Integer> inventoryCounts = new HashMap<>();
        for (Slot slot : menuSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (!carried.isEmpty()) {
            inventoryCounts.merge(carried.getItem(), carried.getCount(), Integer::sum);
        }
        ContextMap context = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            context = SlotDisplayContext.fromLevel(mc.level);
        }

        LOG.warn("══════════════════════════════════════════════");
        LOG.warn("[BRBE-DIAG] 物品栏刷新 — 配方状态合格性检测开始");
        LOG.warn("[BRBE-DIAG] slotHash=0x{} 集合数={} 库存物品={}",
                Long.toHexString(hash), processedCollections.size(), inventoryCounts.size());

        int total = 0, valid = 0, invalid = 0, skipped = 0;
        Map<String, List<String>> invalidByCollection = new LinkedHashMap<>();

        for (RecipeCollection collection : processedCollections) {
            RecipeCollectionAccessor acc = (RecipeCollectionAccessor) collection;
            Set<RecipeDisplayId> craftableSet = acc.brbe$getCraftable();

            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                RecipeDisplayId id = entry.id();
                total++;

                // ── 调查 1：独立材料预测 ──
                PredictedState predicted = predictState(entry.display(), inventoryItems, inventoryCounts, context);
                if (predicted == PredictedState.UNKNOWN) {
                    skipped++;
                    continue;
                }

                // ── 调查 2：BRBE 实际标记 ──
                boolean inCraftable = craftableSet.contains(id);
                boolean isPartial = PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, id);
                boolean needsGrid = PartialCraftingUtil.needsLargerGrid(entry.display());

                // ── 合格性判定 ──
                String label;
                boolean ok;

                if (needsGrid) {
                    // 3×3 配方：2×2 生存网格放不下（工作台是 3×3 网格，3×3 配方可正常放置）。
                    if (mc.gui.screen() instanceof InventoryScreen
                            && !BetterRecipeBook.config.showAllRecipesInSurvival) {
                        // showAll 关闭的 2×2 背包网格：3×3 完全不应出现
                        ok = !inCraftable && !isPartial;
                        label = "3×3配方(showAll关闭, 2×2网格, 不应出现)";
                    } else {
                        // 材料齐全 → 不标 partial（网格问题，incompatible 警告处理）
                        // 部分材料在库存 → 应标 partial（确实缺材料）
                        // 无材料在库存 → 不标 partial（不是"缺部分材料"）
                        ok = switch (predicted) {
                            case PARTIAL -> isPartial;
                            default -> !isPartial;
                        };
                        label = switch (predicted) {
                            case CRAFTABLE -> "3×3配方(网格决定, 材料齐全)";
                            case PARTIAL -> "3×3配方(材料不足, " + (isPartial ? "已标partial" : "未标partial") + ")";
                            default -> "3×3配方(无材料在库存)";
                        };
                    }
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
                    String name = getDisplayName(entry);
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

    // ═══════════ 调查 1：独立材料预测 ═══════════

    private static PredictedState predictState(RecipeDisplay display,
                                               Set<Item> inventoryItems,
                                               Map<Item, Integer> inventoryCounts,
                                               ContextMap context) {
        List<SlotDisplay> ingredients = extractIngredients(display);
        if (ingredients.isEmpty()) return PredictedState.UNKNOWN;
        if (context == null) return PredictedState.UNKNOWN;

        // 统计每个物品在所有成分槽中出现的次数。形状配方里同一物品占多个槽
        // = 需要多份（如 2×羊毛地毯）。数量不足的配方应预测为 PARTIAL。
        Map<Item, Integer> neededCounts = new HashMap<>();
        int totalSlots = 0;
        for (SlotDisplay slot : ingredients) {
            List<ItemStack> variants = resolveVariants(slot, context);
            // 空槽（无候选物品）跳过——不算成分槽
            if (variants.isEmpty()) continue;
            totalSlots++;
            // 成分的候选物品：取第一个在库存中存在的（类型匹配）
            Item chosen = variants.stream()
                    .filter(s -> !s.isEmpty())
                    .map(ItemStack::getItem)
                    .filter(inventoryItems::contains)
                    .findFirst().orElse(null);
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

        // 已匹配槽位数 = 各物品在成分槽出现次数之和（每槽最多匹配一种物品）。
        // 不能用 neededCounts.size()——单一物品多槽位配方（剪刀 2×铁锭）
        // 类型数 1 ≠ 槽位数 2，会误判为 PARTIAL。
        int matchedSlots = neededCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (matchedSlots == totalSlots) return PredictedState.CRAFTABLE;
        if (matchedSlots == 0) return PredictedState.UNCRAFTABLE;
        return PredictedState.PARTIAL;
    }

    private static List<SlotDisplay> extractIngredients(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.ingredients();
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients();
        }
        return List.of(); // furnace/smithing/stonecutter 等无法用此方式推测
    }

    private static List<ItemStack> resolveVariants(SlotDisplay slot, ContextMap context) {
        try {
            return slot.resolveForStacks(context);
        } catch (Exception e) {
            return List.of();
        }
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

    private static String getDisplayName(RecipeDisplayEntry entry) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
                if (!results.isEmpty()) return results.get(0).getHoverName().getString();
            }
        } catch (Exception ignored) {}
        return entry.id().toString();
    }
}
