/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Suppliers
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeHolder
 *  net.minecraft.world.item.crafting.RecipeType
 */
package mezz.jei.api.recipe.types;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

public interface IRecipeHolderType<T extends Recipe<?>>
extends IRecipeType<RecipeHolder<T>> {
    public static <R extends Recipe<?>> IRecipeHolderType<R> create(RecipeType<R> vanillaRecipeType) {
        return new JeiRecipeHolderType<R>(vanillaRecipeType);
    }

    public static <R extends Recipe<?>> IRecipeHolderType<R> create(Identifier recipeId) {
        return new JeiRecipeHolderType(recipeId);
    }

    public static <R extends Recipe<?>> Supplier<IRecipeHolderType<R>> createDeferred(Supplier<RecipeType<R>> vanillaRecipeType) {
        return Suppliers.memoize(() -> IRecipeHolderType.create((RecipeType)vanillaRecipeType.get()));
    }

    public record JeiRecipeHolderType<T extends Recipe<?>>(Identifier uid) implements IRecipeHolderType<T>
    {
        JeiRecipeHolderType(RecipeType<T> vanillaRecipeType) {
            this(JeiRecipeHolderType.getUid(vanillaRecipeType));
        }

        private static Identifier getUid(RecipeType<?> recipeType) {
            Identifier uid = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
            if (uid == null) {
                throw new IllegalArgumentException("Vanilla Recipe Type must be registered before using it here. %s".formatted(recipeType));
            }
            return uid;
        }

        @Override
        public Identifier getUid() {
            return this.uid;
        }

        @Override
        public Class<? extends RecipeHolder<T>> getRecipeClass() {
            @SuppressWarnings({"unchecked", "RedundantCast"})
            Class<? extends RecipeHolder<T>> castClass = (Class<? extends RecipeHolder<T>>) (Object) RecipeHolder.class;
            return castClass;
        }
    }
}

