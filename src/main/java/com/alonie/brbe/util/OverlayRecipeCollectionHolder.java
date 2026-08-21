package com.alonie.brbe.util;

import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the most recently used {@link RecipeCollection} passed to
 * {@code OverlayRecipeComponent.init()}.  Avoids the need to access
 * the outer {@code OverlayRecipeComponent} from an inner button class,
 * which fails on Fabric where {@code this$0} cannot be remapped.
 */
public final class OverlayRecipeCollectionHolder {
    private static RecipeCollection currentCollection;

    private OverlayRecipeCollectionHolder() {
    }

    public static void set(@Nullable RecipeCollection collection) {
        currentCollection = collection;
    }

    @Nullable
    public static RecipeCollection get() {
        return currentCollection;
    }
}
