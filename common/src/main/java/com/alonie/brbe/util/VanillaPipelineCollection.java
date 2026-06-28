package com.alonie.brbe.util;

import com.alonie.brbe.generic.pins.PipelineCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Objects;

/**
 * Adapter that wraps a vanilla {@link RecipeCollection} to expose the
 * {@link PipelineCollection} interface, delegating craftability checks
 * to vanilla methods and partial-craftability checks to
 * {@link PartialCraftingUtil}.
 *
 * <p>Instances are cheap and intended to be created on-the-fly during
 * pipeline execution.  They hold a reference to the wrapped collection
 * but do not own it — mutations to the delegate are visible through
 * this adapter.
 */
public final class VanillaPipelineCollection implements PipelineCollection {

    private final RecipeCollection delegate;

    private VanillaPipelineCollection(RecipeCollection delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    /** Create an adapter for the given vanilla collection. */
    public static VanillaPipelineCollection of(RecipeCollection collection) {
        return new VanillaPipelineCollection(collection);
    }

    /** Expose the underlying vanilla collection for stages that need it. */
    public RecipeCollection unwrap() {
        return delegate;
    }

    // ---- PipelineCollection ----

    @Override
    public List<RecipeHolder<?>> getRecipes() {
        return delegate.getRecipes();
    }

    @Override
    public boolean hasAnyCraftable() {
        return delegate.hasCraftable();
    }

    @Override
    public boolean hasAnyPartiallyCraftable() {
        return PartialCraftingUtil.hasPartialMaterials(delegate);
    }

    // ---- Pinnable ----

    @Override
    public boolean has(ResourceLocation resourceLocation) {
        for (RecipeHolder<?> recipe : delegate.getRecipes()) {
            if (recipe.id().equals(resourceLocation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VanillaPipelineCollection that)) return false;
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
