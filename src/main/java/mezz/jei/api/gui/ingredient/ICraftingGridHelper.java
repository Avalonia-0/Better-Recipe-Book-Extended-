/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Pair
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.item.crafting.display.SlotDisplay
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.gui.ingredient;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

public interface ICraftingGridHelper {
    public List<IRecipeSlotBuilder> createAndSetNamedIngredients(IRecipeLayoutBuilder var1, List<Pair<String, Ingredient>> var2, int var3, int var4);

    public void createAndSetIngredients(IRecipeLayoutBuilder var1, List<Ingredient> var2, int var3, int var4);

    public void createAndSetIngredientsFromDisplays(IRecipeLayoutBuilder var1, List<SlotDisplay> var2, int var3, int var4);

    default public List<IRecipeSlotBuilder> createAndSetNamedInputs(IRecipeLayoutBuilder builder, List<@Nullable Pair<String, List<@Nullable ItemStack>>> namedInputs, int width, int height) {
        return this.createAndSetNamedInputs(builder, VanillaTypes.ITEM_STACK, namedInputs, width, height);
    }

    public <T> List<IRecipeSlotBuilder> createAndSetNamedInputs(IRecipeLayoutBuilder var1, IIngredientType<T> var2, List<@Nullable Pair<String, List<@Nullable T>>> var3, int var4, int var5);

    default public List<IRecipeSlotBuilder> createAndSetInputs(IRecipeLayoutBuilder builder, List<@Nullable List<@Nullable ItemStack>> inputs, int width, int height) {
        return this.createAndSetInputs(builder, VanillaTypes.ITEM_STACK, inputs, width, height);
    }

    public <T> List<IRecipeSlotBuilder> createAndSetInputs(IRecipeLayoutBuilder var1, IIngredientType<T> var2, List<@Nullable List<@Nullable T>> var3, int var4, int var5);

    public <T> void setInputs(List<IRecipeSlotBuilder> var1, IIngredientType<T> var2, List<@Nullable List<@Nullable T>> var3, int var4, int var5);

    default public IRecipeSlotBuilder createAndSetOutputs(IRecipeLayoutBuilder builder, @Nullable List<@Nullable ItemStack> outputs) {
        return this.createAndSetOutputs(builder, VanillaTypes.ITEM_STACK, outputs);
    }

    public IRecipeSlotBuilder createAndSetOutputs(IRecipeLayoutBuilder var1, SlotDisplay var2);

    public <T> IRecipeSlotBuilder createAndSetOutputs(IRecipeLayoutBuilder var1, IIngredientType<T> var2, @Nullable List<@Nullable T> var3);
}

