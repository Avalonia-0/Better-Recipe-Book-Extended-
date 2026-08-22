/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.Identifier
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.item.ItemStack
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.ingredients;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import mezz.jei.api.constants.Tags;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface IIngredientHelper<V> {
    public IIngredientType<V> getIngredientType();

    public String getDisplayName(V var1);

    public Object getUid(V var1, UidContext var2);

    default public Object getUid(ITypedIngredient<V> typedIngredient, UidContext context) {
        return this.getUid(typedIngredient.getIngredient(), context);
    }

    default public Object getGroupingUid(V ingredient) {
        return this.getUid(ingredient, UidContext.Ingredient);
    }

    default public Object getGroupingUid(ITypedIngredient<V> typedIngredient) {
        return this.getGroupingUid(typedIngredient.getIngredient());
    }

    default public boolean hasSubtypes(V ingredient) {
        return this.getIngredientType() instanceof IIngredientTypeWithSubtypes;
    }

    default public String getDisplayModId(V ingredient) {
        return this.getIdentifier(ingredient).getNamespace();
    }

    default public long getAmount(V ingredient) {
        return -1L;
    }

    default public V copyWithAmount(V ingredient, long amount) {
        return this.copyIngredient(ingredient);
    }

    default public Iterable<Integer> getColors(V ingredient) {
        return Collections.emptyList();
    }

    public Identifier getIdentifier(V var1);

    @Deprecated(since="27.0.0", forRemoval=true)
    default public Identifier getResourceLocation(V ingredient) {
        return this.getIdentifier(ingredient);
    }

    default public ItemStack getCheatItemStack(V ingredient) {
        return ItemStack.EMPTY;
    }

    public V copyIngredient(V var1);

    default public V normalizeIngredient(V ingredient) {
        return ingredient;
    }

    default public boolean isValidIngredient(V ingredient) {
        return true;
    }

    default public boolean isIngredientOnServer(V ingredient) {
        return true;
    }

    default public Stream<Identifier> getTagStream(V ingredient) {
        return Stream.empty();
    }

    default public boolean isHiddenFromRecipeViewersByTags(V ingredient) {
        return this.getTagStream(ingredient).anyMatch(arg_0 -> ((Identifier)Tags.HIDDEN_FROM_RECIPE_VIEWERS).equals(arg_0));
    }

    default public boolean isHiddenFromRecipeViewersByTags(ITypedIngredient<V> ingredient) {
        return this.isHiddenFromRecipeViewersByTags(ingredient.getIngredient());
    }

    public String getErrorInfo(@Nullable V var1);

    default public Optional<TagKey<?>> getTagKeyEquivalent(Collection<V> ingredients) {
        return Optional.empty();
    }
}

