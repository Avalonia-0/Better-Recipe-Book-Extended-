package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-only {@link IRecipeLayoutBuilder}: runs a plugin's
 * {@code setRecipe(builder, recipe, focuses)} and captures every declared slot
 * (visible and invisible) as {@link SlotData}, including each slot's layout
 * position.  Layout / shapeless icon / focus-link concerns are no-ops.
 */
public final class DataOnlyLayoutBuilder implements IRecipeLayoutBuilder {

    private final int width;
    private final int height;
    /** Slot-display resolution context (level-backed), so recipe categories
     *  that resolve SlotDisplays through {@code getContextMap()} (e.g. JEI's
     *  vanilla smithing category extension) work during collection. */
    private final ContextMap contextMap;
    private final List<DataOnlySlotBuilder> slots = new ArrayList<>();
    private final List<DataOnlyIngredientAcceptor> invisible = new ArrayList<>();

    public DataOnlyLayoutBuilder(int width, int height) {
        this(width, height, buildContext());
    }

    public DataOnlyLayoutBuilder(int width, int height, ContextMap contextMap) {
        this.width = width;
        this.height = height;
        this.contextMap = contextMap;
    }

    /** The current level's slot-display context, or null when no level is up
     *  (SlotDisplays then resolve with the null-context fallback). */
    private static ContextMap buildContext() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : SlotDisplayContext.fromLevel(mc.level);
        } catch (Exception e) {
            return null;
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public IRecipeSlotBuilder addSlot(RecipeIngredientRole role) {
        DataOnlySlotBuilder slot = new DataOnlySlotBuilder(role, contextMap);
        slots.add(slot);
        return slot;
    }

    @Override
    public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole recipeIngredientRole) {
        DataOnlyIngredientAcceptor acceptor = new DataOnlyIngredientAcceptor(recipeIngredientRole, new ItemStackCollector());
        invisible.add(acceptor);
        return acceptor;
    }

    @Override
    public void moveRecipeTransferButton(int posX, int posY) {
    }

    @Override
    public void setShapeless() {
    }

    @Override
    public void setShapeless(int posX, int posY) {
    }

    @Override
    public void createFocusLink(IIngredientAcceptor<?>... slots) {
    }

    /** Extract the collected slot data (visible + invisible) after setRecipe. */
    public List<SlotData> slotData() {
        List<SlotData> out = new ArrayList<>();
        for (DataOnlySlotBuilder slot : slots) {
            out.add(new SlotData(slot.role(), slot.x(), slot.y(), slot.collector().stacks()));
        }
        for (DataOnlyIngredientAcceptor acceptor : invisible) {
            out.add(new SlotData(acceptor.role(), -1, -1, acceptor.collector().stacks()));
        }
        return out;
    }
}
