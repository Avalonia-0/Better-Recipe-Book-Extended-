/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.gui.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Unmodifiable;

public interface IRecipeSlotsView {
    public @Unmodifiable List<IRecipeSlotView> getSlotViews();

    default public List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role) {
        ArrayList<IRecipeSlotView> list = new ArrayList<IRecipeSlotView>();
        for (IRecipeSlotView slotView : this.getSlotViews()) {
            if (slotView.getRole() != role) continue;
            list.add(slotView);
        }
        return list;
    }

    default public Optional<IRecipeSlotView> findSlotByName(String slotName) {
        return this.getSlotViews().stream().filter(slot -> slot.getSlotName().map(slotName::equals).orElse(false)).findFirst();
    }
}

