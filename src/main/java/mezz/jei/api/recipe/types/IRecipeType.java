/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeType
 */
package mezz.jei.api.recipe.types;

import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

public interface IRecipeType<T> {
    public Identifier getUid();

    public Class<? extends T> getRecipeClass();

    public static <R extends Recipe<?>> IRecipeHolderType<R> create(RecipeType<R> vanillaRecipeType) {
        return IRecipeHolderType.create(vanillaRecipeType);
    }

    public static <T> IRecipeType<T> create(Identifier uid, Class<? extends T> recipeClass) {
        return new JeiRecipeType<T>(uid, recipeClass);
    }

    public static <T> IRecipeType<T> create(String nameSpace, String path, Class<? extends T> recipeClass) {
        Identifier uid = Identifier.fromNamespaceAndPath((String)nameSpace, (String)path);
        return IRecipeType.create(uid, recipeClass);
    }

    public record JeiRecipeType<T>(Identifier uid, Class<? extends T> recipeClass) implements IRecipeType<T>
    {
        @Override
        public Identifier getUid() {
            return this.uid;
        }

        @Override
        public Class<? extends T> getRecipeClass() {
            return this.recipeClass;
        }
    }
}

