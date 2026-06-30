package com.alonie.brbe.generic;

import com.google.common.collect.ImmutableList;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.Pinnable;
import com.alonie.brbe.generic.pins.PipelineCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public abstract class GenericRecipeBookCollection<R extends GenericRecipe, M extends AbstractContainerMenu> implements Pinnable, PipelineCollection {
    protected final RegistryAccess registryAccess;
    protected List<R> recipes;
    protected M menu;

    protected GenericRecipeBookCollection(List<? extends R> list, M menu, RegistryAccess registryAccess) {
        this.menu = menu;
        this.recipes = ImmutableList.copyOf(list);
        this.registryAccess = registryAccess;
    }

    public List<R> getRecipes() {
        return recipes;
    }

    protected abstract List<R> getDisplayRecipes(boolean craftable);

    public boolean has(ResourceLocation resourceLocation) {
        for (R recipe : getRecipes()) {
            if (recipe.id().equals(resourceLocation)) {
                return true;
            }
        }

        return false;
    }

    public R getFirst() {
        return this.getRecipes().get(0);
    }

    protected abstract boolean atleastOneCraftable(NonNullList<Slot> slots);

    protected boolean atleastOnePartiallyCraftable(NonNullList<Slot> slots) {
        return false;
    }

    public List<R> getPartiallyCraftableRecipes(NonNullList<Slot> slots) {
        return List.of();
    }

    /**
     * Returns partially-craftable recipes in this collection, or empty if
     * the feature is disabled in config.  All renderers should call this
     * no-arg variant rather than the slots variant directly.
     */
    public List<R> getPartiallyCraftableRecipes() {
        if (!BetterRecipeBook.ctx().config().partialMarkingEnabled) {
            return List.of();
        }
        return this.getPartiallyCraftableRecipes(this.menu.slots);
    }

    // ---- PipelineCollection ----

    @Override
    public boolean hasAnyCraftable() {
        return atleastOneCraftable(this.menu.slots);
    }

    @Override
    public boolean hasAnyPartiallyCraftable() {
        return atleastOnePartiallyCraftable(this.menu.slots);
    }
}
