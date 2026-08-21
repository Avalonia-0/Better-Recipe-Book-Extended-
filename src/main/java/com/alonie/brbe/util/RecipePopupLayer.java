package com.alonie.brbe.util;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupGeometry;
import com.alonie.brbe.render.PopupRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/**
 * The query-viewer's hover popup as an independent layer (not a button-hover
 * variant): it holds the hovered recipe's button and renders the enlarged popup
 * over the viewer, with its own hit area ({@link #contains}, the shared
 * {@link PopupGeometry} — equal to the rendered texture) and modal click
 * interception.  Triggered and kept alive by {@code RecipeViewerOverlay}'s
 * hover-popup signal (only while Shift is held); closed when the cursor leaves
 * the popup or Shift is released.
 */
public final class RecipePopupLayer {

    private RecipePopupLayer() {}

    private static AbstractWidget button;
    private static RecipeDisplayId id;
    private static RecipeDisplayEntry entry;
    private static int mode = PinOverlay.MODE_CRAFTING;
    private static boolean craftable;
    private static boolean partial;
    private static List<?> slots;
    private static boolean active;

    /** Set the popup to {@code btn}'s recipe, or close it when null. */
    public static void update(AbstractWidget btn) {
        if (!(btn instanceof OverlayRecipeButtonAccessor oba)) {
            close();
            return;
        }
        button = btn;
        id = oba.brbe$getRecipe();
        entry = RecipeViewerEngine.entryFor(id);
        slots = oba.brbe$getSlots();
        craftable = oba.brbe$getCraftable();
        OverlayRecipeComponent outer = oba.brbe$getOuterComponent();
        RecipeCollection collection = outer.getRecipeCollection();
        partial = computePartial(outer, collection);
        mode = computeMode();
        active = true;
    }

    public static void close() {
        active = false;
        button = null;
        id = null;
        entry = null;
        slots = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static AbstractWidget button() {
        return button;
    }

    public static RecipeDisplayId recipeId() {
        return id;
    }

    /** Render the enlarged popup over the viewer (no button magnify; the popup
     *  is its own layer) at the fixed {@link PopupGeometry#VANILLA_SCALE} — the
     *  Shift magnify is gone. */
    public static void render(GuiGraphicsExtractor gui, float delta) {
        if (!active || button == null || id == null) return;
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) button).brbe$getOuterComponent();
        int selIdx = ((OverlayRecipeComponentAccessor) outer).getSlotSelectTime().currentIndex();
        PopupRenderer.renderRecipePopup(gui, id, entry, mode, craftable, partial,
                slots, selIdx, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                true, PopupGeometry.VANILLA_SCALE);
    }

    /** Whether the cursor is inside the popup (its modal area = hit volume). */
    public static boolean contains(double mx, double my) {
        return active && button != null
                && geometry().contains(mx, my);
    }

    /** The item rendered under the cursor inside the popup (cycled variant). */
    public static ItemStack itemAt(int mx, int my) {
        if (!active || button == null) return ItemStack.EMPTY;
        return geometry().itemAt(mx, my, currentSlotSelectIndex());
    }

    private static PopupGeometry geometry() {
        return PopupGeometry.of(id, entry, mode, slots,
                button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    private static int currentSlotSelectIndex() {
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) button).brbe$getOuterComponent();
        return ((OverlayRecipeComponentAccessor) outer).getSlotSelectTime().currentIndex();
    }

    private static boolean computePartial(OverlayRecipeComponent outer, RecipeCollection collection) {
        if (RecipeViewerOverlay.isFurnaceMode()
                || ((OverlayRecipeComponentAccessor) outer).isFurnaceMenu()) {
            return false;
        }
        if (RecipeViewerIndex.isViewerCollection(collection)) {
            return RecipeViewerIndex.isViewerPartial(collection, id)
                    || PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, id);
        }
        return PartialCraftingUtil.isPartiallyCraftable(collection, id);
    }

    private static int computeMode() {
        if (RecipeViewerOverlay.isFurnaceMode()) return PinOverlay.MODE_FURNACE;
        if (RecipeViewerOverlay.isStonecuttingMode()) return PinOverlay.MODE_STONECUTTING;
        if (RecipeViewerOverlay.isSmithingMode()) return PinOverlay.MODE_SMITHING;
        return PinOverlay.MODE_CRAFTING;
    }
}
