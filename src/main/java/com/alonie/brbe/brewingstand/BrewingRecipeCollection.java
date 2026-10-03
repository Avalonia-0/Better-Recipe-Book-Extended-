package com.alonie.brbe.brewingstand;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipeBookCollection;
import com.alonie.brbe.generic.pins.Pinnable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class BrewingRecipeCollection extends GenericRecipeBookCollection<BrewableResult, BrewingStandMenu> implements Pinnable {
    private final BRBBookCategories.Category category;

    public BrewingRecipeCollection(List<BrewableResult> list, BrewingStandMenu menu, RegistryAccess registryAccess, BRBBookCategories.Category category) {
        super(list, menu, registryAccess);

        this.category = category;
    }

    /** 子集重建（排序原因剥离 stage：子组 / 重打包的原组）。 */
    @Override
    public BrewingRecipeCollection subset(List<BrewableResult> subset) {
        BrewingRecipeCollection copy = new BrewingRecipeCollection(subset, this.menu, this.registryAccess, this.category);
        // 继承"剥离子组"标记：子组的子集仍是子组，重打包的原组仍是原组
        copy.brbe$inheritExtractionState(this);
        return copy;
    }

    @Override
    public boolean has(Identifier Identifier) {
        for (BrewableResult recipe : this.recipes) {
            if (recipe.id().equals(Identifier)) {
                return true;
            }
        }

        return false;
    }

    /** 材料齐备判据。可合成 / 残缺 / 两个子列表都由 {@link GenericRecipeBookCollection} 缓存派生。 */
    @Override
    protected boolean hasMaterials(BrewableResult recipe, NonNullList<Slot> slots) {
        return recipe.hasMaterials(this.category, slots, this.menu.getCarried());
    }

    @Override
    protected boolean hasPartialMaterials(BrewableResult recipe, NonNullList<Slot> slots) {
        return recipe.hasPartialMaterials(this.category, slots, this.menu.getCarried());
    }
}
