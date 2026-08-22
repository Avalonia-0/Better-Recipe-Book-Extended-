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
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Unmodifiable;

public interface IRecipeSlotDrawablesView {
    public @Unmodifiable List<IRecipeSlotDrawable> getSlots();

    default public List<IRecipeSlotDrawable> getSlots(RecipeIngredientRole role) {
        ArrayList<IRecipeSlotDrawable> list = new ArrayList<IRecipeSlotDrawable>();
        for (IRecipeSlotDrawable slotView : this.getSlots()) {
            if (slotView.getRole() != role) continue;
            list.add(slotView);
        }
        return list;
    }

    default public Optional<IRecipeSlotDrawable> findSlotByName(String slotName) {
        return this.getSlots().stream().filter(slot -> slot.getSlotName().map(slotName::equals).orElse(false)).findFirst();
    }
}

