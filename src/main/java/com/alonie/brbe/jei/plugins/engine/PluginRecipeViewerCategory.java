package com.alonie.brbe.jei.plugins.engine;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * A dynamic {@link RecipeViewerCategory} wrapping one or more mod JEI
 * {@code IRecipeCategory}s.  Usually one category = one recipe type; several
 * recipe types that share a workstation block (e.g. bclib's leveled anvils,
 * one type per level) are merged into a single category so the player sees one
 * "Anvil" tab instead of one per level.  Queries union the results of every
 * held recipe type.
 */
public final class PluginRecipeViewerCategory implements RecipeViewerCategory {

    private final List<String> uids;
    private final Component name;
    private final ItemStack icon;
    private final List<ItemStack> stations;

    public PluginRecipeViewerCategory(List<String> uids, Component title, List<ItemStack> stations) {
        this.uids = List.copyOf(uids);
        this.name = title;
        this.stations = dedupeStations(stations);
        this.icon = this.stations.isEmpty()
                ? new ItemStack(Items.CRAFTING_TABLE)
                : this.stations.get(0);
    }

    private static List<ItemStack> dedupeStations(List<ItemStack> stations) {
        if (stations == null || stations.isEmpty()) return List.of();
        List<ItemStack> out = new ArrayList<>();
        java.util.Set<net.minecraft.resources.Identifier> seen = new java.util.HashSet<>();
        for (ItemStack stack : stations) {
            if (stack == null || stack.isEmpty()) continue;
            net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && seen.add(id)) {
                out.add(stack);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public String id() {
        // A merged category is identified by its first recipe type; re-collection
        // of the same plugin set produces the same id so the registry replaces
        // rather than duplicates.
        return uids.get(0);
    }

    /** The recipe types this category wraps (used by the category-visibility
     *  pass to enumerate every object of the category). */
    public List<String> uids() {
        return uids;
    }

    /** JEI 类别排序 key：本类别包装的 JEI 类型 uid。 */
    @Override
    public List<String> jeiTypeUids() {
        return uids;
    }

    /** The workstations this plugin category was registered with (the viewer's
     *  left station-column list for dynamic mod categories). */
    public List<ItemStack> stations() {
        return stations;
    }

    @Override
    public ItemStack icon() {
        return icon;
    }

    @Override
    public Component name() {
        return name;
    }

    @Override
    public List<RecipeDisplayEntry> allEntries() {
        List<RecipeDisplayEntry> out = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.crafting.display.RecipeDisplayId> seen =
                new java.util.HashSet<>();
        for (String uid : uids) {
            for (RecipeDisplayEntry entry : RecipeViewerEngine.allRecipes(uid)) {
                if (seen.add(entry.id())) out.add(entry);
            }
        }
        return gateByKnown(out);
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        List<RecipeDisplayEntry> out = new ArrayList<>();
        for (String uid : uids) {
            out.addAll(usage
                    ? RecipeViewerEngine.usagesFor(uid, target)
                    : RecipeViewerEngine.resultsFor(uid, target));
        }
        return gateByKnown(out);
    }

    /** 配方书驱动的 mod 类别：数据源接入配方书（known）——只有已解锁配方显示
     *  （未开 unlockAll 时；与酿造门控同哲学：引擎数据全量、展示级按已解锁过滤，
     *  因此不再"解锁≥1 即全量可见"）。非配方书驱动的 mod 类别（如 bclib 铁砧）
     *  不受影响（KNOWN 从未出现其条目，也无法归属）。 */
    private List<RecipeDisplayEntry> gateByKnown(List<RecipeDisplayEntry> hits) {
        if (hits == null || hits.isEmpty()) return hits;
        if (BetterRecipeBook.config.unlockAll) return hits;
        boolean bookDriven = false;
        for (String uid : uids) {
            if (RecipeViewerEngine.isRecipeBookType(uid)) {
                bookDriven = true;
                break;
            }
        }
        if (!bookDriven) return hits;
        java.util.Set<net.minecraft.world.item.Item> knownResults = new java.util.HashSet<>();
        for (String uid : uids) {
            knownResults.addAll(
                    com.alonie.brbe.cache.RecipeViewerIndex.knownResultItemsForType(uid));
        }
        if (knownResults.isEmpty()) return List.of();
        List<RecipeDisplayEntry> out = new ArrayList<>();
        for (RecipeDisplayEntry entry : hits) {
            try {
                for (ItemStack result : entry.resultItems(null)) {
                    if (result != null && !result.isEmpty()
                            && knownResults.contains(result.getItem())) {
                        out.add(entry);
                        break;
                    }
                }
            } catch (Exception | LinkageError ignored) {
            }
        }
        return out;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        // Source-level exclusion: with "hide objects of workstations without a
        // recipe book" on, a workstation without its own recipe book (every
        // mod workstation — e.g. BetterEnd's end stone smelter) is dropped from
        // the whole system up front: no category matches it, so its objects
        // never surface anywhere.  RecipeViewerIndex.workstations() does the
        // same for vanilla-type matching; this closes the mod-type side.
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                && (target == null || target.isEmpty()
                    || !RecipeViewerEngine.isRecipeBookStation(target))) {
            return false;
        }
        for (String uid : uids) {
            if (RecipeViewerEngine.isStation(uid, target)) return true;
        }
        return false;
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        return stations;
    }

    @Override
    public int defaultPriority(ItemStack target) {
        for (String uid : uids) {
            if (RecipeViewerEngine.hasContent(uid, target, false)
                    || RecipeViewerEngine.hasContent(uid, target, true)) {
                return 1;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof PluginRecipeViewerCategory other
                && uids.equals(other.uids));
    }

    @Override
    public int hashCode() {
        return uids.hashCode();
    }
}
