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
        List<RecipeViewerEngine.IndexedRecipe> smelting = new ArrayList<>();
        List<RecipeViewerEngine.IndexedRecipe> stonecutting = new ArrayList<>();
        List<RecipeViewerEngine.IndexedRecipe> smithing = new ArrayList<>();
        for (RecipeHolder<?> holder : all) {
            RecipeType<?> type = holder.value().getType();
            if (type == RecipeType.CRAFTING) {
                crafting.add(toIndexed(holder, registryAccess));
            } else if (type == RecipeType.SMELTING
                    || type == RecipeType.BLASTING
                    || type == RecipeType.SMOKING
                    || type == RecipeType.CAMPFIRE_COOKING) {
                smelting.add(toIndexed(holder, registryAccess));
            } else if (type == RecipeType.STONECUTTING) {
                stonecutting.add(toIndexed(holder, registryAccess));
            } else if (type == RecipeType.SMITHING) {
                smithing.add(toIndexed(holder, registryAccess));
            }
            // 其余类型（blasting/smoking/campfire 并入 smelting 类别等）后续补齐
        }
        if (!crafting.isEmpty()) {
            RecipeViewerEngine.registerType("minecraft:crafting", crafting, stationsForCrafting());
        }
        if (!smelting.isEmpty()) {
            RecipeViewerEngine.registerType("minecraft:smelting", smelting, stationsForFurnace());
        }
        if (!stonecutting.isEmpty()) {
            RecipeViewerEngine.registerType("minecraft:stonecutting", stonecutting, stationsForStonecutting());
        }
        if (!smithing.isEmpty()) {
            RecipeViewerEngine.registerType("minecraft:smithing", smithing, stationsForSmithing());
        }

        BetterRecipeBook.LOGGER.info("[BRBE-ENGINE] rebuildEngine: total={} crafting={} smelting={} stonecutting={} smithing={}",
                all.size(), crafting.size(), smelting.size(), stonecutting.size(), smithing.size());
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

    /** Furnace-family station blocks. */
    private static List<ItemStack> stationsForFurnace() {
        List<ItemStack> stations = new ArrayList<>();
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.FURNACE));
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.BLAST_FURNACE));
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.SMOKER));
        return stations;
    }

    /** Stonecutter station block. */
    private static List<ItemStack> stationsForStonecutting() {
        List<ItemStack> stations = new ArrayList<>();
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.STONECUTTER));
        return stations;
    }

    /** Smithing-table station block. */
    private static List<ItemStack> stationsForSmithing() {
        List<ItemStack> stations = new ArrayList<>();
        stations.add(new ItemStack(net.minecraft.world.level.block.Blocks.SMITHING_TABLE));
        return stations;
    }

    /** Static furnace-fuel helper（1.21.1 用 AbstractFurnaceBlockEntity.isFuel）。 */
    public static boolean isFuelItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack);
    }

    /** Every registered fuel item（getFuel() map keys），1.21.1 版。 */
    public static List<ItemStack> allFuelItems() {
        List<ItemStack> fuels = new ArrayList<>();
        for (var entry : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                fuels.add(new ItemStack(entry.getKey()));
            }
        }
        return fuels;
    }

    /** Burn duration (ticks) of {@code fuel}, or 0. */
    public static int burnDuration(ItemStack fuel) {
        return (fuel == null || fuel.isEmpty()) ? 0
                : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel()
                        .getOrDefault(fuel.getItem(), 0);
    }
}
