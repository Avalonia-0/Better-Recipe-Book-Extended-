package com.alonie.recipebookispain_extended.mixin.groups;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPain.FurnaceVariant;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.access.ItemAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import com.alonie.brbe.util.BrbeLogger;

@Mixin(value = ClientRecipeBook.class, priority = 999)
public class ClientRecipeBookMixin {

    @Shadow @Final private Map<RecipeDisplayId, RecipeDisplayEntry> known;
    @Shadow private Map<ExtendedRecipeBookCategory, List<RecipeCollection>> collectionsByTab;

    @Unique
    private static final ContextMap RBIP_EMPTY_CONTEXT = ContextMap.EMPTY;

    /**
     * Rebuild Polymer namespace cache at the start of every recipe-book refresh.
     */
    @Inject(at = @At("HEAD"), method = "rebuildCollections")
    private void rbip$preRefreshPolymerCache(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        RecipeBookIsPain.buildNamespaceCache();
        RecipeBookIsPain.applyNamespaceOverrides();
    }

    // NOTE: the creative-group rebuild below must run on EVERY
    // rebuildCollections.  Vanilla rebuildCollections always resets
    // collectionsByTab to its own (RBIP-free) version first, so skipping
    // this rebuild while the known set is unchanged would leave
    // collectionsByTab without the mirrored creative-tab categories and every
    // RBIP tab would be hidden on the next updateTabs.  The per-packet
    // rebuild storm is handled upstream by
    // ClientPacketListenerMixin.brbe$skipUnchangedRefresh (cancels
    // refreshRecipeBook entirely while the known set is unchanged), so this
    // rebuild only runs when the known set actually changed.

    @Inject(at = @At("TAIL"), method = "rebuildCollections")
    private void rbip$refreshCreativeGroups(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        RecipeBookIsPain.ensureInitialized();
        if (RecipeBookIsPain.RECIPE_BOOK_GROUP_TO_ITEM_GROUP.isEmpty()) return;

        // Clear all furnace-type active tabs so we rebuild from scratch
        RecipeBookIsPain.FURNACE_ACTIVE_TABS.clear();
        RecipeBookIsPain.SMOKER_ACTIVE_TABS.clear();
        RecipeBookIsPain.BLAST_FURNACE_ACTIVE_TABS.clear();

        Map<ExtendedRecipeBookCategory, EntryBucket> buckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> furnaceBuckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> smokerBuckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> blastFurnaceBuckets = new LinkedHashMap<>();

        for (RecipeDisplayEntry entry : this.known.values()) {
            // Skip stonecutter and smithing — they use different display formats
            RecipeDisplay display = entry.display();
            if (display instanceof StonecutterRecipeDisplay
                    || display instanceof SmithingRecipeDisplay) continue;

            // ⚠️ 这里**不得**按网格大小过滤配方（2026-09-25 修正）。
            // 本方法跑在 ClientRecipeBook.rebuildCollections 时机，只认 known 集合，
            // **看不到当前打开的是 2×2 背包还是 3×3 工作台**；此前在这里丢掉
            // "需要更大网格"的配方，导致：① RBIP 标签页在工作台上也永远不显示 3×3
            // 配方；② 唯一配方是 3×3 的创造标签组（如"刷怪蛋"里的嘎枝之心）整组为空
            // → 标签直接消失。
            // 网格可见性由**显示路径**按当前菜单判定：
            // pipeline/RecipeBookComponentMixin.brbe$applyGridVisibility（Stage 0）
            // —— 2×2 + showAllRecipesInSurvival=false 时把放不下的配方从
            // selected/craftable 剔除，3×3 时原样放行；RBIP 的合成组走同一条管线。

            if (display instanceof FurnaceRecipeDisplay) {
                // Determine which furnace type this recipe belongs to via its vanilla category
                FurnaceVariant variant = rbip$determineFurnaceVariant(entry.category());
                ExtendedRecipeBookCategory group = rbip$getFurnaceGroupForEntry(entry, variant);
                if (group == null) continue;
                Map<ExtendedRecipeBookCategory, EntryBucket> targetBuckets = switch (variant) {
                    case SMOKER -> smokerBuckets;
                    case BLAST_FURNACE -> blastFurnaceBuckets;
                    default -> furnaceBuckets;
                };
                // Group by output item so recipes producing the same output merge into one button
                String outputKey = BuiltInRegistries.ITEM.getKey(
                    entry.resultItems(RBIP_EMPTY_CONTEXT).iterator().next().getItem()).toString();
                rbip$bucketFor(targetBuckets, group).add(entry, outputKey);
            } else {
                // Only include recipes belonging to the vanilla crafting recipe book.
                // Mod-added recipe books (e.g. Farmer's Delight cooking pot) have custom
                // RecipeBookCategory instances that differ from the vanilla crafting ones.
                ExtendedRecipeBookCategory cat = entry.category();
                if (cat != RecipeBookCategories.CRAFTING_BUILDING_BLOCKS
                        && cat != RecipeBookCategories.CRAFTING_REDSTONE
                        && cat != RecipeBookCategories.CRAFTING_EQUIPMENT
                        && cat != RecipeBookCategories.CRAFTING_MISC
                        && cat != SearchRecipeBookCategory.CRAFTING) continue;

                ExtendedRecipeBookCategory group = rbip$getGroupForEntry(entry);
                if (group == null) continue;
                rbip$bucketFor(buckets, group).add(entry);
            }
        }

        if (buckets.isEmpty() && furnaceBuckets.isEmpty()
                && smokerBuckets.isEmpty() && blastFurnaceBuckets.isEmpty()) return;

        Map<ExtendedRecipeBookCategory, List<RecipeCollection>> updatedResults = new HashMap<>(this.collectionsByTab);
        updatedResults.remove(RecipeBookCategories.FURNACE_FOOD);
        updatedResults.remove(RecipeBookCategories.FURNACE_BLOCKS);
        updatedResults.remove(RecipeBookCategories.FURNACE_MISC);
        updatedResults.remove(RecipeBookCategories.SMOKER_FOOD);
        updatedResults.remove(RecipeBookCategories.BLAST_FURNACE_BLOCKS);
        updatedResults.remove(RecipeBookCategories.BLAST_FURNACE_MISC);

        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : buckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : furnaceBuckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : smokerBuckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : blastFurnaceBuckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        this.collectionsByTab = Map.copyOf(updatedResults);
    }

    /**
     * {@code get} + 按需 {@code put}（不用 {@code computeIfAbsent}）。
     *
     * <p>Mixin 类里**不要写 lambda**：Mixin 会把 mixin 内的合成 lambda 方法重命名并在
     * latest.log 打一行 INFO（{@code Renaming synthetic method ... from mod brbe}），
     * 每个 lambda 一行——本类原先 6 个 lambda 就是 6 行噪声。</p>
     */
    @Unique
    private static EntryBucket rbip$bucketFor(Map<ExtendedRecipeBookCategory, EntryBucket> map,
                                              ExtendedRecipeBookCategory group) {
        EntryBucket bucket = map.get(group);
        if (bucket == null) {
            bucket = new EntryBucket();
            map.put(group, bucket);
        }
        return bucket;
    }

    @Unique
    private static FurnaceVariant rbip$determineFurnaceVariant(RecipeBookCategory category) {        if (category == RecipeBookCategories.SMOKER_FOOD) return FurnaceVariant.SMOKER;
        if (category == RecipeBookCategories.BLAST_FURNACE_BLOCKS
                || category == RecipeBookCategories.BLAST_FURNACE_MISC) return FurnaceVariant.BLAST_FURNACE;
        // Default: furnace (covers FURNACE_FOOD, FURNACE_BLOCKS, FURNACE_MISC, and unknown)
        return FurnaceVariant.FURNACE;
    }

    @Unique
    private static ExtendedRecipeBookCategory rbip$getGroupForEntry(RecipeDisplayEntry entry) {
        try {
            for (ItemStack stack : entry.resultItems(RBIP_EMPTY_CONTEXT)) {
                ExtendedRecipeBookCategory group = RecipeBookIsPain.toRecipeBookGroup(stack);
                if (group != null) return group;
            }
        } catch (Exception e) {
            BrbeLogger.log("RBIP", "Could not resolve output stack for recipe display {}", entry.id(), e);
        }
        return null;
    }

    @Unique
    private static ExtendedRecipeBookCategory rbip$getFurnaceGroupForEntry(RecipeDisplayEntry entry, FurnaceVariant variant) {
        try {
            for (ItemStack stack : entry.resultItems(RBIP_EMPTY_CONTEXT)) {
                ExtendedRecipeBookCategory group = RecipeBookIsPain.toFurnaceRecipeBookGroup(stack, variant);
                if (group != null) {
                    // Track that this creative tab has recipes for this furnace type
                    CreativeModeTab tab = switch (variant) {
                        case SMOKER -> RecipeBookIsPain.SMOKER_BOOK_GROUP_TO_ITEM_GROUP.get(group);
                        case BLAST_FURNACE -> RecipeBookIsPain.BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(group);
                        default -> RecipeBookIsPain.FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(group);
                    };
                    if (tab != null) {
                        switch (variant) {
                            case SMOKER -> RecipeBookIsPain.SMOKER_ACTIVE_TABS.add(tab);
                            case BLAST_FURNACE -> RecipeBookIsPain.BLAST_FURNACE_ACTIVE_TABS.add(tab);
                            default -> RecipeBookIsPain.FURNACE_ACTIVE_TABS.add(tab);
                        }
                    }

                    return group;
                }
            }
        } catch (Exception e) {
            BrbeLogger.log("RBIP", "Could not resolve furnace output for {}", entry.id(), e);
        }
        return null;
    }

    private static class EntryBucket {
        private final List<List<RecipeDisplayEntry>> entries = new ArrayList<>();
        private final Map<Integer, List<RecipeDisplayEntry>> groupedEntries = new LinkedHashMap<>();
        private final Map<String, List<RecipeDisplayEntry>> groupedByOutput = new LinkedHashMap<>();

        /** Standard grouping by entry.group() — used for crafting/brewing recipes. */
        private void add(RecipeDisplayEntry entry) {
            OptionalInt group = entry.group();
            if (group.isEmpty()) {
                this.entries.add(List.of(entry));
                return;
            }

            List<RecipeDisplayEntry> grouped = this.groupedEntries.get(group.getAsInt());
            if (grouped == null) {
                grouped = new ArrayList<>();
                this.groupedEntries.put(group.getAsInt(), grouped);
                this.entries.add(grouped);
            }
            grouped.add(entry);
        }

        /** Grouping by output item key — used for furnace recipes to merge duplicates
         * that arise from the same output appearing in multiple vanilla categories.
         * Within each output group, duplicates with identical ingredient + result
         * content are skipped (ignoring metadata fields like duration/station/xp). */
        private void add(RecipeDisplayEntry entry, String outputKey) {
            List<RecipeDisplayEntry> grouped = this.groupedByOutput.get(outputKey);
            if (grouped == null) {
                grouped = new ArrayList<>();
                this.groupedByOutput.put(outputKey, grouped);
                this.entries.add(grouped);
            } else {
                // Deduplicate: skip if an entry with the same content already exists.
                // FurnaceRecipeDisplay is a Record — its equals() compares ALL fields
                // (ingredient, result, duration, station, xp), which is too strict.
                // We only care about ingredient + result for dedup purposes.
                RecipeDisplay newDisplay = entry.display();
                if (newDisplay instanceof FurnaceRecipeDisplay newFd) {
                    for (RecipeDisplayEntry existing : grouped) {
                        if (existing.display() instanceof FurnaceRecipeDisplay existingFd) {
                            if (newFd.ingredient().equals(existingFd.ingredient())
                                    && newFd.result().equals(existingFd.result())) {
                                return; // Same ingredient + result → skip
                            }
                        }
                    }
                } else {
                    // Non-furnace display: use standard equals
                    for (RecipeDisplayEntry existing : grouped) {
                        if (newDisplay.equals(existing.display())) return;
                    }
                }
            }
            grouped.add(entry);
        }

        /** Debug: expose groupedByOutput entries for duplicate checking. */
        java.util.Set<Map.Entry<String, List<RecipeDisplayEntry>>> groupedByOutputList() {
            return this.groupedByOutput.entrySet();
        }

        private List<RecipeCollection> toCollections() {
            // 用显式循环而不是 stream+lambda：mixin 内的 lambda 会被 Mixin 重命名并刷
            // 一行 INFO（见 rbip$bucketFor 的说明）。
            List<RecipeCollection> out = new ArrayList<>(this.entries.size());
            for (List<RecipeDisplayEntry> group : this.entries) {
                out.add(new RecipeCollection(List.copyOf(group)));
            }
            return List.copyOf(out);
        }
    }
}
