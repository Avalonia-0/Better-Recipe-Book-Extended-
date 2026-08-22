/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.serialization.Codec
 *  net.minecraft.client.renderer.Rect2i
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Unmodifiable
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.runtime;

import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

public interface IIngredientManager {
    default public @Unmodifiable Collection<ItemStack> getAllItemStacks() {
        return this.getAllIngredients(VanillaTypes.ITEM_STACK);
    }

    public <V> @Unmodifiable Collection<V> getAllIngredients(IIngredientType<V> var1);

    public <V> @Unmodifiable Collection<ITypedIngredient<V>> getAllTypedIngredients(IIngredientType<V> var1);

    public <V> IIngredientHelper<V> getIngredientHelper(V var1);

    public <V> IIngredientHelper<V> getIngredientHelper(IIngredientType<V> var1);

    public <V> IIngredientRenderer<V> getIngredientRenderer(V var1);

    public <V> IIngredientRenderer<V> getIngredientRenderer(IIngredientType<V> var1);

    public <V> Codec<V> getIngredientCodec(IIngredientType<V> var1);

    public @Unmodifiable Collection<IIngredientType<?>> getRegisteredIngredientTypes();

    public Optional<IIngredientType<?>> getIngredientTypeForUid(String var1);

    public <V> void addIngredientsAtRuntime(IIngredientType<V> var1, Collection<V> var2);

    public <V> void removeIngredientsAtRuntime(IIngredientType<V> var1, Collection<V> var2);

    public <V> @Nullable IIngredientType<V> getIngredientType(V var1);

    public <V> Optional<IIngredientType<V>> getIngredientTypeChecked(V var1);

    public <B, I> Optional<IIngredientTypeWithSubtypes<B, I>> getIngredientTypeWithSubtypesFromBase(B var1);

    public <V> Optional<IIngredientType<V>> getIngredientTypeChecked(Class<? extends V> var1);

    public <V> Optional<ITypedIngredient<V>> createTypedIngredient(IIngredientType<V> var1, V var2, boolean var3);

    default public <T> Optional<ITypedIngredient<T>> createTypedIngredient(T ingredient, boolean normalize) {
        return this.getIngredientTypeChecked(ingredient).flatMap(ingredientType -> this.createTypedIngredient((IIngredientType)ingredientType, (Object)ingredient, normalize));
    }

    @Deprecated(forRemoval=true, since="21.2.0")
    default public <V> Optional<ITypedIngredient<V>> createTypedIngredient(IIngredientType<V> ingredientType, V ingredient) {
        return this.createTypedIngredient(ingredientType, ingredient, false);
    }

    @Deprecated(forRemoval=true, since="21.2.0")
    default public <V> Optional<ITypedIngredient<V>> createTypedIngredient(V ingredient) {
        return this.createTypedIngredient(ingredient, false);
    }

    public <V> ITypedIngredient<V> normalizeTypedIngredient(ITypedIngredient<V> var1);

    public IClickableIngredientFactory getClickableIngredientFactory();

    @Deprecated(forRemoval=true, since="21.2.0")
    public <V> Optional<IClickableIngredient<V>> createClickableIngredient(IIngredientType<V> var1, V var2, Rect2i var3, boolean var4);

    @Deprecated(forRemoval=true, since="21.2.0")
    default public <V> Optional<IClickableIngredient<V>> createClickableIngredient(V ingredient, Rect2i area, boolean normalize) {
        return this.getIngredientTypeChecked(ingredient).flatMap(type -> this.createClickableIngredient((IIngredientType)type, ingredient, area, normalize));
    }

    public Collection<String> getIngredientAliases(ITypedIngredient<?> var1);

    public void registerIngredientListener(IIngredientListener var1);

    public static interface IIngredientListener {
        public <V> void onIngredientsAdded(IIngredientHelper<V> var1, Collection<ITypedIngredient<V>> var2);

        public <V> void onIngredientsRemoved(IIngredientHelper<V> var1, Collection<ITypedIngredient<V>> var2);
    }
}

