package com.alonie.brbe.mixins.rei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import net.minecraft.client.gui.GuiGraphics;
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

            if (BetterRecipeBook.RECIPE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openRecipeView(hoveredStack));
            } else if (BetterRecipeBook.USAGE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openUsageView(hoveredStack));
            }
            return;
        }

        // ── 2. Ghost items ─────────────────────────────────────────────
        ItemStack ghostStack = this.brbe$hoveredGhostStack;
        if (ghostStack != null && !ghostStack.isEmpty()) {
            if (BetterRecipeBook.RECIPE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openRecipeView(ghostStack));
            } else if (BetterRecipeBook.USAGE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                cir.setReturnValue(ItemViewCompat.openUsageView(ghostStack));
            }
        }
    }
}
