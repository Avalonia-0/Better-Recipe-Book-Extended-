package com.alonie.brbe.smithingtable;

import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;

import java.util.List;

public class SmithingRecipeCollection extends GenericRecipeBookCollection<BRBSmithingRecipe, SmithingMenu> {
    public SmithingRecipeCollection(List<? extends BRBSmithingRecipe> list, SmithingMenu menu, RegistryAccess registryAccess) {
        super(list, menu, registryAccess);
    }

    /** 子集重建（排序原因剥离 stage：子组 / 重打包的原组）。 */
    @Override
    public SmithingRecipeCollection subset(List<BRBSmithingRecipe> subset) {
        SmithingRecipeCollection copy = new SmithingRecipeCollection(subset, this.menu, this.registryAccess);
        // 继承"剥离子组"标记：子组的子集仍是子组，重打包的原组仍是原组
        copy.brbe$inheritExtractionState(this);
        return copy;
    }

    /** 材料齐备判据。可合成 / 残缺 / 两个子列表都由 {@link GenericRecipeBookCollection} 缓存派生。 */
    @Override
    protected boolean hasMaterials(BRBSmithingRecipe recipe, NonNullList<Slot> slots) {
        return recipe.hasMaterials(slots, registryAccess, this.menu.getCarried());
    }

    @Override
    protected boolean hasPartialMaterials(BRBSmithingRecipe recipe, NonNullList<Slot> slots) {
        return recipe.hasPartialMaterials(slots, registryAccess, this.menu.getCarried());
    }
}
