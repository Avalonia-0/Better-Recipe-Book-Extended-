/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.FormattedText
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.recipe.vanilla;

import java.util.List;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.FormattedText;
import org.jetbrains.annotations.Unmodifiable;

public interface IJeiIngredientInfoRecipe {
    public @Unmodifiable List<ITypedIngredient<?>> getIngredients();

    public @Unmodifiable List<FormattedText> getDescription();
}

