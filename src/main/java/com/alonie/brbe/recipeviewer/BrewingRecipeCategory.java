package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

/**
 * The brewing category (JEI {@code minecraft:brewing} type): every potion
 * recipe JEI generates from the world's potion-brewing registry.  R = which
 * brewing recipes produce {@code target} (as output potion); U = what
 * {@code target} brews into (as ingredient or input potion).
 *
 * <p>Recipe entries are registered directly by the JEI-plugin indexer from JEI's
 * own vanilla plugin (runtime-built recipes, no datapack holders) — each entry
 * carries its native JEI layout + render entry, so preview / pin show the
 * complete JEI UI for <b>every</b> object (brewing-stand background, slot
 * backgrounds, bubbles, arrow, brewing-steps text) with no post-hoc layout
 * matching.  Unlock consistency with the brewing book is enforced at both
 * query time and browse-all (Ctrl+O {@code allEntries}) time by the
 * {@code RecipeUnlockTracker} gate — a recipe-book system category shows only
 * unlocked recipes unless unlockAll is on.  The brewing stand is a
 * no-recipe-book workstation:
 * with "hide objects of workstations without a recipe book" its station
 * connection is cut (like the stonecutter) and its recipe objects are
 * filtered from every query.</p>
 */
public final class BrewingRecipeCategory implements RecipeViewerCategory {

    private static final String TYPE = "minecraft:brewing";

    @Override
    public String id() {
        return "brewing";
    }

    @Override
    public java.util.List<String> jeiTypeUids() {
        return List.of("minecraft:brewing");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BREWING_STAND);
    }

    @Override
    public Component name() {
        return Component.translatable("brbe.category.brewing");
    }

    @Override
    public List<RecipeDisplayEntry> query(ItemStack target, boolean usage) {
        List<RecipeDisplayEntry> hits = usage
                ? RecipeViewerEngine.usagesFor(TYPE, target)
                : RecipeViewerEngine.resultsFor(TYPE, target);
        return gateUnlocked(hits);
    }

    /** 进度门控：只保留已解锁（材料获得过）的产物药水——酿造是配方书体系
     *  （BRBE 自带酿造书），按"配方书类别的查询只显示已解锁配方"规则（未开
     *  unlockAll 时）过滤；unlockAll 恒真。查询与浏览（Ctrl+O allEntries）
     *  共用——数据源级（引擎注册全量）但展示级恒门控，任何路径都不泄漏未解锁。
     */
    private static List<RecipeDisplayEntry> gateUnlocked(List<RecipeDisplayEntry> hits) {
        java.util.List<RecipeDisplayEntry> out = new java.util.ArrayList<>();
        for (RecipeDisplayEntry entry : hits) {
            if (com.alonie.brbe.brewingstand.RecipeUnlockTracker.isPotionResultUnlocked(
                    potionIdOf(entry))) {
                out.add(entry);
            }
        }
        return out;
    }

    /** entry 产物（结果药水堆）中的药水 id，解析失败返回 null（不门控）。 */
    private static Identifier potionIdOf(RecipeDisplayEntry entry) {
        if (entry == null) return null;
        try {
            for (ItemStack stack : entry.resultItems(null)) {
                if (stack == null || stack.isEmpty()) continue;
                net.minecraft.world.item.alchemy.PotionContents contents =
                        stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                if (contents == null) continue;
                java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>> potion =
                        contents.potion();
                if (potion.isPresent()) {
                    return net.minecraft.core.registries.BuiltInRegistries.POTION
                            .getKey(potion.get().value());
                }
            }
        } catch (Exception | LinkageError ignored) {
        }
        return null;
    }

    @Override
    public List<RecipeDisplayEntry> allEntries() {
        return gateUnlocked(RecipeViewerEngine.allRecipes(TYPE));
    }

    @Override
    public boolean appliesTo(ItemStack target) {
        return RecipeViewerEngine.hasContent(TYPE, target, false)
                || RecipeViewerEngine.hasContent(TYPE, target, true);
    }

    @Override
    public boolean appliesToMenu(AbstractContainerMenu menu) {
        return menu instanceof BrewingStandMenu;
    }

    @Override
    public boolean appliesToStation(ItemStack target) {
        return TYPE.equals(RecipeViewerIndex.stationTypeIdFor(target));
    }

    @Override
    public List<ItemStack> stationIconsFor(RecipeDisplayEntry entry) {
        // The brewing-stand station icon: the engine entries are synthetic
        // (recipe-book category "crafting_misc"), so the category-path lookup
        // would mis-attribute the crafting table.
        return List.of(new ItemStack(Items.BREWING_STAND));
    }

    @Override
    public int defaultPriority(ItemStack target) {
        return appliesTo(target) ? 1 : -1;
    }
}
