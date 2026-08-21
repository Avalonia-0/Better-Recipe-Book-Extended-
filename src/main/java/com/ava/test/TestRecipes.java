package com.ava.test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 测试用占位配方（JVM 参数启用）。
 *
 * <p>通过 {@code -Dava.test.recipes=<数量>} 启用（例如 2000 个占位配方用于测试
 * 多配方下的体验）；未传参或数量 ≤0 时完全禁用，不影响正常游戏。后续测试功能
 * 各用一个 {@code -Dava.test.<feature>=<配置>} 参数。</p>
 *
 * <p>注入的 {@link RecipeDisplayEntry} 使用<b>负</b> {@link RecipeDisplayId}：
 * 与 {@code VanillaRecipeCache} 的本地配方语义一致，点击时经
 * {@code MultiPlayerGameModeMixin} 走客户端 ghost 放置、不请求服务端；group 为空
 * 使每个条目在配方书中独立成按钮（2000 条 = 100 页）。</p>
 */
public final class TestRecipes {

    /** JVM 参数名：值为占位配方数量。 */
    public static final String JVM_RECIPES = "ava.test.recipes";

    /** 负 ID 基准，远离 VanillaRecipeCache 的 -1.. 范围，避免互相覆盖。 */
    private static final int FIRST_ID = -1_000_000;

    private static final Logger LOG = LogManager.getLogger("ava-test");

    /** 结果物品循环集，便于肉眼区分页码与测试搜索。 */
    private static final Item[] RESULT_CYCLE = {
            Items.DIRT, Items.STONE, Items.COBBLESTONE, Items.OAK_PLANKS,
            Items.SAND, Items.GRAVEL, Items.IRON_INGOT, Items.GOLD_INGOT,
    };

    private static List<RecipeDisplayEntry> entries;
    private static Map<RecipeDisplayId, RecipeDisplayEntry> byId;

    private TestRecipes() {
    }

    /** 读取 JVM 参数得到的占位配方数量；未启用时为 0。 */
    public static int count() {
        String raw = System.getProperty(JVM_RECIPES);
        if (raw == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            LOG.warn("[AVA-TEST] invalid {}={}, disabled", JVM_RECIPES, raw);
            return 0;
        }
    }

    public static boolean enabled() {
        return count() > 0;
    }

    /** 注入占位配方到配方书 known 映射（幂等）。 */
    public static void injectInto(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        if (!enabled()) return;
        Map<RecipeDisplayId, RecipeDisplayEntry> map = byId();
        if (map.isEmpty()) return;
        known.putAll(map);
    }

    public static synchronized List<RecipeDisplayEntry> entries() {
        if (entries == null) build();
        return entries;
    }

    public static synchronized Map<RecipeDisplayId, RecipeDisplayEntry> byId() {
        if (byId == null) build();
        return byId;
    }

    private static void build() {
        int n = count();
        List<RecipeDisplayEntry> list = new ArrayList<>(n);
        Map<RecipeDisplayId, RecipeDisplayEntry> map = new HashMap<>(n);
        SlotDisplay ingredient = new SlotDisplay.ItemSlotDisplay(Items.DIRT);
        for (int i = 0; i < n; i++) {
            RecipeDisplayId id = new RecipeDisplayId(FIRST_ID - i);
            Item resultItem = RESULT_CYCLE[i % RESULT_CYCLE.length];
            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(
                    List.of(ingredient),
                    new SlotDisplay.ItemSlotDisplay(resultItem),
                    SlotDisplay.Empty.INSTANCE);
            RecipeDisplayEntry entry = new RecipeDisplayEntry(
                    id, display, OptionalInt.empty(), RecipeBookCategories.CRAFTING_MISC, Optional.empty());
            list.add(entry);
            map.put(id, entry);
        }
        entries = Collections.unmodifiableList(list);
        byId = Collections.unmodifiableMap(map);
        LOG.info("[AVA-TEST] built {} placeholder recipes (-D{}={})", n, JVM_RECIPES, n);
    }
}
