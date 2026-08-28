package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * A dynamic {@link RecipeViewerCategory} wrapping one or more mod JEI
 * {@code IRecipeCategory}s (1.21.1 JEI-19.27 bridge).  Usually one category =
 * one recipe type; several recipe types that share a workstation block are
 * merged into a single category.  Queries union the results of every held
 * recipe type through the JEI channel (RecipeViewerEngine.jeiResultsFor /
 * jeiUsagesFor / allJeiRecipes).
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
        java.util.Set<net.minecraft.resources.ResourceLocation> seen = new java.util.HashSet<>();
        for (ItemStack stack : stations) {
            if (stack == null || stack.isEmpty()) continue;
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && seen.add(id)) {
                out.add(stack);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public String id() {
        return uids.get(0);
    }

    /** The recipe types this category wraps. */
    public List<String> uids() {
        return uids;
    }

    /** The workstations this plugin category was registered with. */
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
    public List<RecipeHolder<?>> query(ItemStack target, boolean usage) {
        // 1.21.1 的 mod JEI 类别走 JEI 通道（JeiEntry），本身无 RecipeHolder 条目。
        return List.of();
    }

    @Override
    public List<RecipeViewerEngine.JeiEntry> queryJei(ItemStack target, boolean usage) {
        List<RecipeViewerEngine.JeiEntry> out = new ArrayList<>();
        for (String uid : uids) {
            out.addAll(usage
                    ? RecipeViewerEngine.jeiUsagesFor(uid, target)
                    : RecipeViewerEngine.jeiResultsFor(uid, target));
        }
        return out;
    }

    @Override
    public List<RecipeViewerEngine.JeiEntry> allJeiEntries() {
        List<RecipeViewerEngine.JeiEntry> out = new ArrayList<>();
        java.util.Set<RecipeViewerEngine.JeiEntry> seen = new java.util.HashSet<>();
        for (String uid : uids) {
            for (RecipeViewerEngine.JeiEntry entry : RecipeViewerEngine.allJeiRecipes(uid)) {
                if (seen.add(entry)) out.add(entry);
            }
        }
        return out;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        for (String uid : uids) {
            if (RecipeViewerEngine.isStation(uid, target)) return true;
        }
        return false;
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeHolder<?> entry) {
        return stations;
    }

    @Override
    public int defaultPriority(ItemStack target) {
        for (String uid : uids) {
            if (RecipeViewerEngine.hasJeiContent(uid, target, false)
                    || RecipeViewerEngine.hasJeiContent(uid, target, true)) {
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
}
