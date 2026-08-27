package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.21.1 版 view 类别注册表：内置类别（crafting + fuel 骨架，其余类别后续
 * 逐类补齐）+ 外部类别（后续 JEI 插件收集适配后接入）。
 *
 * <p>与 1.21.11 的差异：无 RecipeViewerEngine.isRecipeBookStation 判定
 * （1.21.1 无配方书工作站聚合），hideNoRecipeBookStationObjects 相关过滤
 * 由 UI 层实现或暂缓。</p>
 */
public final class RecipeViewerCategories {

    private RecipeViewerCategories() {}

    /** Built-in categories (vanilla recipe types). */
    private static final List<RecipeViewerCategory> BUILTIN =
            List.of(new CraftingRecipeCategory(), new FurnaceRecipeCategory(),
                    new FuelRecipeCategory(), new StonecuttingRecipeCategory(),
                    new SmithingRecipeCategory(), new CompostRecipeCategory());

    /** Categories appended by the companion mod (mod recipe types). */
    private static final List<RecipeViewerCategory> EXTERNAL = new CopyOnWriteArrayList<>();

    private static volatile List<RecipeViewerCategory> ALL;

    /** All categories: built-in followed by externally registered ones. */
    public static List<RecipeViewerCategory> all() {
        List<RecipeViewerCategory> cached = ALL;
        if (cached == null) {
            cached = buildAll();
            ALL = cached;
        }
        return cached;
    }

    /** Registers categories collected from mod JEI plugins.  Idempotent. */
    public static void registerExternal(List<RecipeViewerCategory> categories) {
        if (categories == null || categories.isEmpty()) return;
        boolean changed = false;
        for (RecipeViewerCategory category : categories) {
            if (category != null && !EXTERNAL.contains(category)) {
                EXTERNAL.add(category);
                changed = true;
            }
        }
        if (changed) ALL = null;
    }

    private static List<RecipeViewerCategory> buildAll() {
        if (EXTERNAL.isEmpty()) return BUILTIN;
        List<RecipeViewerCategory> out = new ArrayList<>(BUILTIN);
        out.addAll(EXTERNAL);
        return List.copyOf(out);
    }

    /**
     * Pick the default category for {@code target} on open.  A workstation
     * block's usage view wins first (JEI semantics); otherwise the applicable
     * category with the highest {@link RecipeViewerCategory#defaultPriority}
     * whose query yields at least one entry.  Returns null when no category
     * can show anything for {@code target} (the viewer does not open).
     */
    public static RecipeViewerCategory defaultFor(ItemStack target, boolean usage,
                                                  AbstractContainerMenu menu) {
        if (usage) {
            RecipeViewerCategory firstMatch = null;
            for (RecipeViewerCategory category : all()) {
                if (!category.appliesToStation(target)) continue;
                if (category.hasContent(target, true)) {
                    return category;
                }
                if (firstMatch == null) {
                    firstMatch = category;
                }
            }
            if (firstMatch != null) {
                RecipeViewerCategory alternative = bestByPriority(target, usage);
                if (alternative != null) {
                    return alternative;
                }
                return firstMatch;
            }
        }
        return bestByPriority(target, usage);
    }

    /** The applicable category with the highest {@link RecipeViewerCategory
     *  #defaultPriority} whose query yields at least one entry, or null. */
    private static RecipeViewerCategory bestByPriority(ItemStack target, boolean usage) {
        RecipeViewerCategory best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (RecipeViewerCategory category : all()) {
            if (!category.appliesTo(target)) continue;
            int priority = category.defaultPriority(target);
            if (priority < 0) continue;
            if (!category.hasContent(target, usage)) continue;
            if (priority > bestPriority) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }
}
