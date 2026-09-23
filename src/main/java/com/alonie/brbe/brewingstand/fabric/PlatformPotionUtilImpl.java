package com.alonie.brbe.brewingstand.fabric;

import com.alonie.brbe.brewingstand.PlatformPotionUtil;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeAccess;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of the potion-brewing provider — 26.3 rewrite.
 *
 * <p>26.2 read the hard-coded {@code PotionBrewing} registry and reflected into
 * its package-private {@code Mix} record.  26.3 deletes both: brewing is now a
 * regular data-driven recipe type ({@code minecraft:brewing}), so a recipe is a
 * {@link BrewingRecipe} carrying
 * <ul>
 *   <li>{@code input} — an input potion ({@code PotionIngredient}, usually
 *       "potion item + potion predicate"),</li>
 *   <li>{@code reagent} — the brewing ingredient,</li>
 *   <li>{@code output} — the resulting potion item, whose
 *       {@code minecraft:potion_contents} component names the produced
 *       potion.</li>
 * </ul>
 * The provider therefore surfaces the recipe manager's brewing recipes instead
 * of reflecting into a registry, and resolves {@code from}/{@code to} from the
 * recipe's potion predicate / output component.  No reflection is needed any
 * more, so there is no {@code FabricPotionBrewingAccessor} counterpart.
 */
public class PlatformPotionUtilImpl implements PlatformPotionUtil.PotionUtilProvider {

    public static void init() {
        PlatformPotionUtil.setProvider(new PlatformPotionUtilImpl());
    }

    @Override
    public Ingredient getIngredient(Object recipe) {
        BrewingRecipe brewing = unwrap(recipe);
        return brewing == null ? null : brewing.getReagent().ingredient();
    }

    /**
     * 基底物品形态：{@code input} 是"物品 + 药水谓词"（{@code PotionIngredient}），
     * 外层 {@code Ingredient} 里的第一个物品就是普通/喷溅/滞留药水中的一种。
     * <b>这就是配方书三个标签页的归属判据</b>——同一个"药水→药水"转换在 26.3 的
     * 配方表里以三种形态各存在一条，不按它过滤就会在同一个标签页里列出三份
     * 一模一样的配方（结果图标又统一按标签页物品绘制）。
     */
    @Override
    public Item getInputItem(Object recipe) {
        BrewingRecipe brewing = unwrap(recipe);
        if (brewing == null) return null;
        return firstItem(brewing.getInput().ingredient());
    }

    @Override
    public Item getOutputItem(Object recipe) {
        BrewingRecipe brewing = unwrap(recipe);
        if (brewing == null) return null;
        return brewing.getOutput().create().getItem();
    }

    /** {@code Ingredient} 的第一个物品（酿造配方的 input 恒为单物品，非标签）。 */
    private static Item firstItem(Ingredient ingredient) {
        if (ingredient == null) return null;
        return ingredient.items().findFirst().map(Holder::value).orElse(null);
    }

    @Override
    public Potion getTo(Object recipe) {
        BrewingRecipe brewing = unwrap(recipe);
        if (brewing == null) return null;
        ItemStack output = brewing.getOutput().create();
        PotionContents contents = output.get(DataComponents.POTION_CONTENTS);
        return contents == null ? null : contents.potion().map(Holder::value).orElse(null);
    }

    @Override
    public Potion getFrom(Object recipe) {
        BrewingRecipe brewing = unwrap(recipe);
        if (brewing == null) return null;
        // input.potions() 是 PotionsPredicate（"物品 + 药水谓词"），药水集合在
        // 谓词里面再取一层。
        return brewing.getInput().potions()
                .flatMap(PotionsPredicate::potions)
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value)
                .orElse(null);
    }

    /** 配方值或 {@link RecipeHolder} 两种入参都接受（调用方历史上都传过）。 */
    private static BrewingRecipe unwrap(Object recipe) {
        Object value = recipe instanceof RecipeHolder<?> holder ? holder.value() : recipe;
        return value instanceof BrewingRecipe brewing ? brewing : null;
    }

    /**
     * 酿造配方来源（26.3）。
     *
     * <p><b>为什么不能只看 {@code level.recipeAccess()}</b>：26.3 客户端
     * {@code ClientLevel.recipeAccess()} 返回的是 vanilla 的
     * {@code ClientRecipeContainer} —— 它**只有配方属性集与切石配方**，没有配方表
     * （26.2 客户端还能拿到 {@code level.potionBrewing()} 的硬编码表）。所以旧写法
     * {@code instanceof RecipeManager} 在客户端**永远不成立** → 返回空列表 →
     * {@code PotionLoader.POTIONS} 为空 → 酿造配方书一条都不显示（"完全不解锁"，
     * 连 {@code unlockAll} 也救不了：根本没有条目）。</p>
     *
     * <p>解析顺序：</p>
     * <ol>
     *   <li>服务端/集成服务端上下文（{@code ServerLevel.recipeAccess()} 就是
     *       {@link RecipeManager}）——直接用；</li>
     *   <li>客户端 → fabric-recipe-api 的同步集（vanilla 的 ClientRecipeContainer
     *       被该 API mixin 成 {@code FabricRecipeAccess}）；</li>
     *   <li>单人兜底 → 集成服务端配方管理器（全量，含数据包/mod 添加的酿造配方）；</li>
     *   <li>都没有 → 空（无 fabric-api 的多人环境，客户端无从得知配方）。</li>
     * </ol>
     */
    @Override
    public List<?> getPotionMixes(Level level) {
        if (level != null && level.recipeAccess() instanceof RecipeManager recipeManager) {
            return brewingOf(recipeManager.getRecipes());
        }
        SynchronizedRecipes synced = synchronizedRecipes(level);
        if (synced != null) {
            List<BrewingRecipe> list = brewingOf(synced.getAllOfType(RecipeType.BREWING));
            if (!list.isEmpty()) return list;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getSingleplayerServer() != null) {
            return brewingOf(minecraft.getSingleplayerServer().getRecipeManager().getRecipes());
        }
        return List.of();
    }

    /** vanilla 的客户端配方容器被 fabric-recipe-api mixin 成该接口，同步集从这里取。 */
    @Nullable
    private static SynchronizedRecipes synchronizedRecipes(@Nullable Level level) {
        if (level == null) return null;
        if (level.recipeAccess() instanceof FabricRecipeAccess access) {
            return access.getSynchronizedRecipes();
        }
        return null;
    }

    /** 过滤出酿造配方（返回未包 holder 的 {@link BrewingRecipe} 值，与历史调用方一致）。 */
    private static List<BrewingRecipe> brewingOf(@Nullable Collection<? extends RecipeHolder<?>> holders) {
        if (holders == null || holders.isEmpty()) return List.of();
        List<BrewingRecipe> out = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder != null && holder.value() instanceof BrewingRecipe brewing) {
                out.add(brewing);
            }
        }
        return out;
    }
}
