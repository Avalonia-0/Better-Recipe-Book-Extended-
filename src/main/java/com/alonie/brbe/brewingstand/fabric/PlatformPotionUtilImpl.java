package com.alonie.brbe.brewingstand.fabric;

import com.alonie.brbe.brewingstand.PlatformPotionUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

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
        return recipe instanceof BrewingRecipe brewing ? brewing.getReagent().ingredient() : null;
    }

    @Override
    public Potion getTo(Object recipe) {
        if (!(recipe instanceof BrewingRecipe brewing)) return null;
        ItemStack output = brewing.getOutput().create();
        PotionContents contents = output.get(DataComponents.POTION_CONTENTS);
        return contents == null ? null : contents.potion().map(Holder::value).orElse(null);
    }

    @Override
    public Potion getFrom(Object recipe) {
        if (!(recipe instanceof BrewingRecipe brewing)) return null;
        // input.potions() 是 PotionsPredicate（"物品 + 药水谓词"），药水集合在
        // 谓词里面再取一层。
        return brewing.getInput().potions()
                .flatMap(PotionsPredicate::potions)
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value)
                .orElse(null);
    }

    @Override
    public List<?> getPotionMixes(Level level) {
        // 26.3: Level 不再有 getRecipeManager()，改为 recipeAccess()
        // （RecipeManager implements RecipeAccess；fabric-api 的 FabricRecipeAccess
        //  同样挂在这个接口上）。
        if (!(level.recipeAccess() instanceof RecipeManager recipeManager)) return List.of();
        return recipeManager.getRecipes().stream()
                .map(RecipeHolder::value)
                .filter(BrewingRecipe.class::isInstance)
                .map(BrewingRecipe.class::cast)
                .toList();
    }
}
