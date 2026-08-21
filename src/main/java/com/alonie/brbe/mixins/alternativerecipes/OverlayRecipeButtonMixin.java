package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.pinoverlay.PinButtonRenderOverride;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupRenderer;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipePopupLayer;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget {

    @Final
    @Shadow
    private boolean isCraftable;
    @Final
    @Shadow
    private RecipeDisplayId recipe;

    @Shadow
    @Final
    private List<Object> slots;

    public OverlayRecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    /** Frozen pin mode while a pin renders, else the live viewer mode.  A pin
     *  must keep rendering in its creation mode even if the query viewer
     *  switches category underneath. */
    private boolean isFurnaceMode() {
        return PinButtonRenderOverride.active()
                ? PinButtonRenderOverride.isFurnace() : RecipeViewerOverlay.isFurnaceMode();
    }

    private boolean isStonecuttingMode() {
        return PinButtonRenderOverride.active()
                ? PinButtonRenderOverride.isStonecutting() : RecipeViewerOverlay.isStonecuttingMode();
    }

    private boolean isSmithingMode() {
        return PinButtonRenderOverride.active()
                ? PinButtonRenderOverride.isSmithing() : RecipeViewerOverlay.isSmithingMode();
    }

    @Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) this).brbe$getOuterComponent();
        RecipeCollection collection = outer.getRecipeCollection();
        boolean furnaceBook = ((OverlayRecipeComponentAccessor) outer).isFurnaceMenu();
        boolean partial = computePartial(outer, collection, furnaceBook);

        boolean pin = PinButtonRenderOverride.active();
        boolean viewer = RecipeViewerIndex.isViewerCollection(collection);
        boolean hover = isHoveredOrFocused() || pin;
        int mode = mode();
        int selIdx = ((OverlayRecipeComponentAccessor) outer).getSlotSelectTime().currentIndex();
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;

        // Pin: frozen hover rendering of the full recipe popup (its geometry is
        // pinned at creation; the query viewer's category can switch beneath).
        // The craftable state is read dynamically from the pin's collection
        // (refreshed on inventory change), not from the cloned button's final
        // isCraftable field, which is frozen at creation.
        if (pin) {
            PopupRenderer.renderRecipePopup(gui, this.recipe, recipeEntry(), mode,
                    collection.isCraftable(this.recipe), partial, this.slots, selIdx, x, y, w, h,
                    true, PinButtonRenderOverride.current());
            ci.cancel();
            return;
        }

        // The popup layer is modal: a button lying under it must not stay
        // hovered (its own popup / tooltip would show through).
        AbstractWidget topPopup = RecipePopupLayer.button();
        if (hover && topPopup != null && topPopup != this
                && RecipePopupLayer.contains(mouseX, mouseY)) {
            hover = false;
        }

        // Query viewer: the button stays at its base size — hovering (without
        // Shift) swaps the backdrop to the _highlighted sprite (per craftable
        // state); the enlarged preview (Shift) is the only zoom feedback.  The
        // popup is drawn by the independent popup layer, which is triggered and
        // kept alive by RecipeViewerOverlay.
        if (viewer) {
            PopupRenderer.renderBaseButton(gui, this.recipe, recipeEntry(), mode,
                    this.isCraftable, partial, this.slots, selIdx, x, y, w, h, hover);
            ci.cancel();
            return;
        }

        // Recipe book: hover magnifies the button as before.
        if (hover) {
            PopupRenderer.renderRecipePopup(gui, this.recipe, recipeEntry(), mode,
                    this.isCraftable, partial, this.slots, selIdx, x, y, w, h,
                    true, ClientCompat.isShiftDown() ? 4f : 2f);
        } else {
            PopupRenderer.renderBaseButton(gui, this.recipe, recipeEntry(), mode,
                    this.isCraftable, partial, this.slots, selIdx, x, y, w, h, false);
        }
        ci.cancel();
    }

    private boolean computePartial(OverlayRecipeComponent outer, RecipeCollection collection,
                                   boolean furnaceBook) {
        if (isFurnaceMode() || furnaceBook) {
            // Furnace books (furnace / blast furnace / smoker) and the viewer's
            // furnace category have no partial-crafting state.
            return false;
        }
        if (RecipeViewerIndex.isViewerCollection(collection)) {
            return RecipeViewerIndex.isViewerPartial(collection, this.recipe)
                    || PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, this.recipe);
        }
        return PartialCraftingUtil.isPartiallyCraftable(collection, this.recipe);
    }

    private int mode() {
        if (isFurnaceMode()) return PinOverlay.MODE_FURNACE;
        if (isStonecuttingMode()) return PinOverlay.MODE_STONECUTTING;
        if (isSmithingMode()) return PinOverlay.MODE_SMITHING;
        return PinOverlay.MODE_CRAFTING;
    }

    /** The RecipeDisplayEntry backing this button (or null). */
    private RecipeDisplayEntry recipeEntry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        RecipeDisplayEntry entry = RecipeViewerEngine.entryFor(this.recipe);
        if (entry != null) return entry;
        return ((ClientRecipeBookAccessor) mc.player.getRecipeBook())
                .brbe$getKnown().get(this.recipe);
    }
}
