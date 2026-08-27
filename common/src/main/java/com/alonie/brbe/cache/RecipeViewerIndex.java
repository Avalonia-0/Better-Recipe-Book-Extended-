package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 1.21.1 版查询索引（RecipeManager 全量 → 引擎类型注册）。
 *
 * <p>对齐 1.21.11 的 RecipeViewerIndex.rebuildEngine 职责，但数据源为
 * 1.21.1 的 {@link RecipeManager#getRecipes()}（RecipeHolder 列表）——无
 * RecipeDisplayEntry/SlotDisplay 体系。类别按 1.21.11 的 typeId 约定注册
 * （minecraft:crafting 等），结果条目从 RecipeHolder 提取 inputs/outputs
 * （getIngredients/getResultItem）。</p>
 */
public final class RecipeViewerIndex {

    private RecipeViewerIndex() {}

    private static boolean dirty = true;

    /** Mark the engine rebuild stale (recipes/known changed). */
    public static void markDirty() {
        dirty = true;
    }

    /** Rebuild the engine if marked dirty (call after recipe sync / unlockAll toggle). */
    public static void flushEngineRebuildIfDirty() {
        if (!dirty) return;
        rebuildEngine();
    }

    /** Rebuild all registered types from the current RecipeManager. */
    public static synchronized void rebuildEngine() {
        dirty = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;
        RecipeManager registry = minecraft.level.getRecipeManager();
        HolderLookup.Provider registryAccess = minecraft.level.registryAccess();

        // All known recipes (synced from server / loaded datapacks)
        Collection<RecipeHolder<?>> all = registry.getRecipes();

        // Per-type grouping
        List<RecipeViewerEngine.IndexedRecipe> crafting = new ArrayList<>();
        for (RecipeHolder<?> holder : all) {
            RecipeType<?> type = holder.value().getType();
            if (type == RecipeType.CRAFTING) {
                crafting.add(toIndexed(holder, registryAccess));
            }
            // 其余类型（smelting/stonecutting/smithing 等）后续类别补齐时续加
        }
        if (!crafting.isEmpty()) {
            RecipeViewerEngine.registerType("minecraft:crafting", crafting, stationsForCrafting());
        }

        BetterRecipeBook.LOGGER.info("[BRBE-ENGINE] rebuildEngine: total={} crafting={}",
                all.size(), crafting.size());
    }

    private static RecipeViewerEngine.IndexedRecipe toIndexed(RecipeHolder<?> holder,
                                                               HolderLookup.Provider registryAccess) {
        Recipe<?> recipe = holder.value();
        List<ItemStack> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack stack : ingredient.getItems()) {
                inputs.add(stack);
                break; // 每个 ingredient 取代表物品（轮循显示由 UI 层处理）
            }
        }
        ItemStack result = recipe.getResultItem(registryAccess);
        List<ItemStack> outputs = result.isEmpty() ? List.of() : List.of(result);
        return new RecipeViewerEngine.IndexedRecipe(holder, inputs, outputs);
    }

    /** Full-brightness crafting-station blocks for the crafting type. */
    private static List<ItemStack> stationsForCrafting() {
        List<ItemStack> stations = new ArrayList<>();
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE));
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.CRAFTER));
        return stations;
    }
}
