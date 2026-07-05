package com.alonie.brbe.mixins.rei;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds REI/JEI recipe/usage view shortcuts to the vanilla crafting recipe book,
 * including ghost items in the crafting grid.
 * Press R to open recipe view, U to open usage view.
 * <p>
 * 1.21.1 variant — uses {@code renderGhostRecipeTooltip(GuiGraphics, int, int, int, int)}
 * to detect which ghost ingredient the mouse is hovering over. The method receives
 * {@code (gui, x, y, mouseX, mouseY)} where x/y is the recipe book position.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    /** The ItemStack of the ghost ingredient currently under the mouse. */
    @Unique
    private ItemStack brbe$hoveredGhostStack;

    /**
     * Safety guard: when REI's AbstractDisplayViewingScreen is open, the
     * recipe book page button list and ghost recipe are out of sync with
     * the underlying container screen.  Vanilla key handling iterates
     * ghost ingredients and indexes into the page button list, which
     * throws IndexOutOfBoundsException when the sizes don't match.
     * Short-circuit keyPressed to prevent this crash.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$guardReiViewingScreen(int keyCode, int scanCode, int modifiers,
                                             CallbackInfoReturnable<Boolean> cir) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        String screenClass = screen.getClass().getName();
        // REI's AbstractDisplayViewingScreen and its subclasses hold a
        // RecipeBookComponent whose page state is not valid for key handling.
        if (screenClass.contains("AbstractDisplayViewingScreen")
                || screenClass.contains("DisplayViewingScreen")) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Track which ghost ingredient the mouse is hovering over during the
     * ghost-recipe tooltip render pass.  The method signature is:
     * {@code renderGhostRecipeTooltip(GuiGraphics, int x, int y, int mouseX, int mouseY)}
     */
    @Inject(method = "renderGhostRecipeTooltip", at = @At("HEAD"))
    private void brbe$captureGhostHover(GuiGraphics gui, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        GhostRecipe ghostRecipe = ((RecipeBookComponentAccessor) this).getGhostRecipe();
        if (ghostRecipe == null || ghostRecipe.size() == 0) {
            this.brbe$hoveredGhostStack = null;
            return;
        }

        for (int idx = 0; idx < ghostRecipe.size(); idx++) {
            GhostRecipe.GhostIngredient ing = ghostRecipe.get(idx);
            int sx = ing.getX() + x;
            int sy = ing.getY() + y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                this.brbe$hoveredGhostStack = ing.getItem();
                return;
            }
        }

        this.brbe$hoveredGhostStack = null;
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void brbe$handleItemViewKeys(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!ItemViewCompat.isLoaded() || cir.getReturnValueZ()) {
            return;
        }

        RecipeBookPage page = ((RecipeBookComponentAccessor) this).getRecipeBookPage();
        if (page == null) {
            return;
        }

        // ── 1. Recipe buttons ──────────────────────────────────────────
        for (RecipeButton button : ((RecipeBookPageAccessor) page).getButtons()) {
            if (!button.isHoveredOrFocused()) {
                continue;
            }
            // Guard against null collection (can happen when page state
            // changes between render and key-press on some modpacks).
            if (button.getCollection() == null || button.getRecipe() == null) {
                continue;
            }

            ItemStack hoveredStack = button.getRecipe().value().getResultItem(button.getCollection().registryAccess());
            if (hoveredStack == null || hoveredStack.isEmpty()) {
                return;
            }

            if (ItemViewCompat.matchesShowRecipe(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openRecipeView(hoveredStack));
            } else if (ItemViewCompat.matchesShowUses(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openUsageView(hoveredStack));
            }
            return;
        }

        // ── 2. Ghost items ─────────────────────────────────────────────
        ItemStack ghostStack = this.brbe$hoveredGhostStack;
        if (ghostStack != null && !ghostStack.isEmpty()) {
            if (ItemViewCompat.matchesShowRecipe(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openRecipeView(ghostStack));
            } else if (ItemViewCompat.matchesShowUses(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openUsageView(ghostStack));
            }
        }
    }
}
