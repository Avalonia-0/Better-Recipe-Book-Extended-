package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 残缺配方幽灵物品的红遮罩增强。
 *
 * <p>合成台点击残缺配方后，原版 {@code GhostSlots} 为每个材料槽位绘制红色背景
 * （0x30FF0000），表示"缺少材料"。本类在每次渲染幽灵物品前统计玩家物品栏各
 * 材料数量，按幽灵槽位从左到右、从上到下的顺序逐个扣除：库存中已有的材料，其
 * 对应槽位的红色遮罩被移除，让玩家一眼看出还剩哪些材料没凑齐。
 *
 * <p>渲染链路：{@code AbstractRecipeBookScreen.extractRenderState} → 本类
 * {@link #prepare}（经 {@code RecipeBookComponentMixin} 注入）→
 * {@code GhostSlots.extractRenderState} → {@link #shouldShowRedMask}（经
 * {@code GhostSlotsMixin} 重定向红色填充）。
 */
public final class PartialGhostOverlayUtil {
    private static boolean active;
    private static final Set<Long> noRedMaskSlots = new HashSet<>();

    /** 反射缓存：GhostSlot record 的 {@code items()} / {@code isResultSlot()} 访问器。 */
    @Nullable
    private static Method itemsMethod;
    @Nullable
    private static Method isResultMethod;

    private PartialGhostOverlayUtil() {
    }

    /**
     * 在渲染幽灵物品前调用，计算当前配方下应移除红色遮罩的槽位。
     * 幽灵物品出现即代表放置失败（材料未齐），始终按 slots+carried 逐个扣除
     * 已有材料——完全可合成的配方放置成功不会渲染幽灵，故无需提前返回。
     * 不依赖 partialMarkingEnabled：用户关闭残缺配方标记后配方回到不可合成
     * 状态，幽灵物品红遮罩仍应正确指示缺失材料。
     */
    public static void prepare(RecipeDisplayId recipe, RecipeCollection collection,
                               NonNullList<Slot> menuSlots, ItemStack carried, GhostSlots ghostSlots) {
        active = false;
        noRedMaskSlots.clear();
        if (recipe == null || collection == null || ghostSlots == null) return;

        // 物品栏各材料总数量（含鼠标拿起物），与 RecipeBookComponentMixin 的统计口径一致。
        Map<Item, Integer> counts = new HashMap<>();
        for (Slot slot : menuSlots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        if (!carried.isEmpty()) counts.merge(carried.getItem(), carried.getCount(), Integer::sum);

        // 幽灵槽位按 (y, x) 排序 = 从上到下、从左到右，即玩家感知的槽位顺序。
        Reference2ObjectMap<Slot, ?> ingredients = ((GhostSlotsAccessor) ghostSlots).getIngredients();
        List<Slot> ordered = new ArrayList<>(ingredients.keySet());
        ordered.sort(Comparator.comparingInt((Slot s) -> s.y).thenComparingInt(s -> s.x));

        // 逐个扣除：库存中还有该材料则移除该槽位红遮罩，并扣减剩余数量。
        active = true;
        for (Slot slot : ordered) {
            Object ghost = ingredients.get(slot);
            if (isResultSlot(ghost)) continue;
            Item item = resolveGhostItem(ghost);
            if (item == null) continue;
            int available = counts.getOrDefault(item, 0);
            if (available > 0) {
                noRedMaskSlots.add(key(slot.x, slot.y));
                counts.put(item, available - 1);
            }
        }
    }

    /**
     * {@code GhostSlots.extractRenderState} 绘制红色背景时调用。
     * true = 保留红遮罩；false = 跳过（该槽位材料已在物品栏中）。
     */
    public static boolean shouldShowRedMask(int x0, int y0) {
        if (!active) return true;
        if (noRedMaskSlots.contains(key(x0, y0))) return false;
        // 结果槽大格子分支以 (slot.x-4, slot.y-4) 传入，偏移回原坐标再匹配。
        if (noRedMaskSlots.contains(key(x0 + 4, y0 + 4))) return false;
        return true;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    @Nullable
    private static Item resolveGhostItem(Object ghost) {
        List<ItemStack> items = ghostItems(ghost);
        if (items == null) return null;
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) return stack.getItem();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static List<ItemStack> ghostItems(Object ghost) {
        Method m = itemsMethod;
        if (m == null || m.getDeclaringClass() != ghost.getClass()) {
            m = findNoArgMethod(ghost.getClass(), java.util.List.class);
            itemsMethod = m;
        }
        if (m == null) return null;
        try {
            m.trySetAccessible();
            return (List<ItemStack>) m.invoke(ghost);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * GhostSlot record 的 {@code isResultSlot()} 访问器。
     * 结果槽不参与材料扣除，始终保留红遮罩（原版行为）。
     */
    private static boolean isResultSlot(Object ghost) {
        Method m = isResultMethod;
        if (m == null || m.getDeclaringClass() != ghost.getClass()) {
            m = findNoArgMethod(ghost.getClass(), boolean.class);
            isResultMethod = m;
        }
        if (m == null) return false;
        try {
            m.trySetAccessible();
            return (boolean) m.invoke(ghost);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Nullable
    private static Method findNoArgMethod(Class<?> clazz, Class<?> returnType) {
        for (Method m : clazz.getMethods()) {
            if (m.getReturnType() == returnType && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }
}
