package com.alonie.recipebookispain_extended.mixin.groups;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPain.FurnaceVariant;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.access.ItemAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.util.context.ContextKeySet;
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

@Mixin(value = ClientRecipeBook.class, priority = 999)
public class ClientRecipeBookMixin {

    @Shadow @Final private Map<RecipeDisplayId, RecipeDisplayEntry> known;
    @Shadow private Map<ExtendedRecipeBookCategory, List<RecipeCollection>> collectionsByTab;

    @Unique
    private static final ContextMap RBIP_EMPTY_CONTEXT = new ContextMap.Builder().create(new ContextKeySet.Builder().build());

    /**
     * Returns true if the given crafting display would be rejected by
     * {@code CraftingRecipeBookComponent.canDisplay()} on a 2×2 grid
     * (survival inventory).  When {@code showAllRecipesInSurvival} is {@code false},
     * these recipes must not appear in creative-tab groups because the player
     * cannot place them, and clicking them causes "air placeholder" ghost items.
     */
    @Unique
    private static boolean rbip$needsLargerGrid(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.width() > 2 || shaped.height() > 2;
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients().size() > 4;
        }
        return false;
    }

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

        RecipeBookIsPain.FURNACE_ACTIVE_TABS.clear();
        RecipeBookIsPain.SMOKER_ACTIVE_TABS.clear();
        RecipeBookIsPain.BLAST_FURNACE_ACTIVE_TABS.clear();

        Map<ExtendedRecipeBookCategory, EntryBucket> buckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> furnaceBuckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> smokerBuckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> blastFurnaceBuckets = new LinkedHashMap<>();

        for (RecipeDisplayEntry entry : this.known.values()) {
            RecipeDisplay display = entry.display();
            if (display instanceof StonecutterRecipeDisplay
                    || display instanceof SmithingRecipeDisplay) continue;

            // When showAllRecipesInSurvival is false, skip crafting recipes that
            // need more than a 2×2 grid.  They cannot be placed in the survival
            // inventory crafting grid, and clicking them produces "air placeholder"
            // ghost items in the recipe book.
            if (com.alonie.brbe.BetterRecipeBook.config != null
                    && !com.alonie.brbe.BetterRecipeBook.config.showAllRecipesInSurvival) {
                if (rbip$needsLargerGrid(display)) continue;
            }

            if (display instanceof FurnaceRecipeDisplay) {
                FurnaceVariant variant = rbip$determineFurnaceVariant(entry.category());
                ExtendedRecipeBookCategory group = rbip$getFurnaceGroupForEntry(entry, variant);
                if (group == null) continue;
                Map<ExtendedRecipeBookCategory, EntryBucket> targetBuckets = switch (variant) {
                    case SMOKER -> smokerBuckets;
                    case BLAST_FURNACE -> blastFurnaceBuckets;
                    default -> furnaceBuckets;
                };
                String outputKey = BuiltInRegistries.ITEM.getKey(
                    entry.resultItems(RBIP_EMPTY_CONTEXT).iterator().next().getItem()).toString();
                targetBuckets.computeIfAbsent(group, ignored -> new EntryBucket()).add(entry, outputKey);
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
                buckets.computeIfAbsent(group, ignored -> new EntryBucket()).add(entry);
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

        buckets.forEach((group, bucket) -> updatedResults.put(group, bucket.toCollections()));
        furnaceBuckets.forEach((group, bucket) -> updatedResults.put(group, bucket.toCollections()));
        smokerBuckets.forEach((group, bucket) -> updatedResults.put(group, bucket.toCollections()));
        blastFurnaceBuckets.forEach((group, bucket) -> updatedResults.put(group, bucket.toCollections()));
        this.collectionsByTab = Map.copyOf(updatedResults);
    }

    @Unique
    private static FurnaceVariant rbip$determineFurnaceVariant(RecipeBookCategory category) {
        if (category == RecipeBookCategories.SMOKER_FOOD) return FurnaceVariant.SMOKER;
        if (category == RecipeBookCategories.BLAST_FURNACE_BLOCKS
                || category == RecipeBookCategories.BLAST_FURNACE_MISC) return FurnaceVariant.BLAST_FURNACE;
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
            RecipeBookIsPain.LOGGER.debug("[RBIP] Could not resolve output stack for recipe display {}", entry.id(), e);
        }
        return null;
    }

    @Unique
    private static ExtendedRecipeBookCategory rbip$getFurnaceGroupForEntry(RecipeDisplayEntry entry, FurnaceVariant variant) {
        try {
            for (ItemStack stack : entry.resultItems(RBIP_EMPTY_CONTEXT)) {
                ExtendedRecipeBookCategory group = RecipeBookIsPain.toFurnaceRecipeBookGroup(stack, variant);
                if (group != null) {
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
            RecipeBookIsPain.LOGGER.debug("[RBIP] Could not resolve furnace output for {}", entry.id(), e);
        }
        return null;
    }

    private static class EntryBucket {
        private final List<List<RecipeDisplayEntry>> entries = new ArrayList<>();
        private final Map<Integer, List<RecipeDisplayEntry>> groupedEntries = new LinkedHashMap<>();
        private final Map<String, List<RecipeDisplayEntry>> groupedByOutput = new LinkedHashMap<>();

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

        private void add(RecipeDisplayEntry entry, String outputKey) {
            List<RecipeDisplayEntry> grouped = this.groupedByOutput.get(outputKey);
            if (grouped == null) {
                grouped = new ArrayList<>();
                this.groupedByOutput.put(outputKey, grouped);
                this.entries.add(grouped);
            } else {
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

        private List<RecipeCollection> toCollections() {
            return this.entries.stream()
                    .map(entries -> new RecipeCollection(List.copyOf(entries)))
                    .toList();
        }
    }
}
