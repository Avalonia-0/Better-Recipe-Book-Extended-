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
package mezz.jei.api.recipe;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

@Deprecated(since="20.0.0", forRemoval=true)
public final class RecipeType<T>
implements IRecipeType<T> {
    final Identifier uid;
    final Class<? extends T> recipeClass;

    @Deprecated(since="20.0.0", forRemoval=true)
    public static <T> RecipeType<T> create(String nameSpace, String path, Class<? extends T> recipeClass) {
        Identifier uid = Identifier.fromNamespaceAndPath((String)nameSpace, (String)path);
        return new RecipeType<T>(uid, recipeClass);
    }

    @Deprecated(since="20.0.0", forRemoval=true)
    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> createFromVanilla(net.minecraft.world.item.crafting.RecipeType<R> vanillaRecipeType) {
        Identifier uid = BuiltInRegistries.RECIPE_TYPE.getKey(vanillaRecipeType);
        if (uid == null) {
            throw new IllegalArgumentException("Vanilla Recipe Type must be registered before using it here. %s".formatted(vanillaRecipeType));
        }
        return RecipeType.createRecipeHolderType(uid);
    }

    @Deprecated(since="20.0.0", forRemoval=true)
    public static <R extends Recipe<?>> RecipeType<RecipeHolder<R>> createRecipeHolderType(Identifier uid) {
        @SuppressWarnings({"unchecked", "RedundantCast"})
        Class<? extends RecipeHolder<R>> holderClass = (Class<? extends RecipeHolder<R>>) (Object) RecipeHolder.class;
        return new RecipeType<RecipeHolder<R>>(uid, holderClass);
    }

    @Deprecated(since="20.0.0", forRemoval=true)
    public static <R extends Recipe<?>> Supplier<RecipeType<RecipeHolder<R>>> createFromDeferredVanilla(Supplier<net.minecraft.world.item.crafting.RecipeType<R>> deferredVanillaRecipeType) {
        return Suppliers.memoize(() -> RecipeType.createFromVanilla((net.minecraft.world.item.crafting.RecipeType)deferredVanillaRecipeType.get()));
    }

    public RecipeType(Identifier uid, Class<? extends T> recipeClass) {
        if (uid == null) {
            throw new NullPointerException("uid must not be null.");
        }
        if (recipeClass == null) {
            throw new NullPointerException("recipeClass must not be null.");
        }
        this.uid = uid;
        this.recipeClass = recipeClass;
    }

    @Override
    public Identifier getUid() {
        return this.uid;
    }

    @Override
    public Class<? extends T> getRecipeClass() {
        return this.recipeClass;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof RecipeType)) {
            return false;
        }
        RecipeType other = (RecipeType)obj;
        return this.recipeClass == other.recipeClass && this.uid.equals((Object)other.uid);
    }

    public int hashCode() {
        return 31 * this.uid.hashCode() + this.recipeClass.hashCode();
    }

    public String toString() {
        return "RecipeType[uid=" + String.valueOf(this.uid) + ", recipeClass=" + String.valueOf(this.recipeClass) + "]";
    }
}

