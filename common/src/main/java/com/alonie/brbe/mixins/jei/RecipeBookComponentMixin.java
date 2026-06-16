package com.alonie.brbe.mixins.jei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.ClientCompat;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Unique
    private Slot betterRecipeBook$hoveredSlot;

    /**
     * Capture the hovered slot during tooltip extraction so it is available
     * in {@code keyPressed} for ghost-item lookup.
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"))
    private void betterRecipeBook$captureHoveredSlot(GuiGraphicsExtractor gui, int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        this.betterRecipeBook$hoveredSlot = slot;
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$handleJeiKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
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

            ItemStack hoveredStack = button.getDisplayStack();
            if (hoveredStack != null && !hoveredStack.isEmpty()) {
                if (ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING, event.key(), event.scancode(), event.modifiers())) {
                    cir.setReturnValue(ItemViewCompat.openRecipeView(hoveredStack));
                } else if (ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING, event.key(), event.scancode(), event.modifiers())) {
                    cir.setReturnValue(ItemViewCompat.openUsageView(hoveredStack));
                }
            }
            return;
        }

        // ── 2. Ghost items ─────────────────────────────────────────────
        Slot slot = this.betterRecipeBook$hoveredSlot;
        if (slot == null) {
            return;
        }

        // Get the GhostSlots instance and check for a ghost at the hovered slot
        GhostSlots ghostSlots = ((RecipeBookComponentAccessor) this).getGhostSlots();
        if (ghostSlots == null) {
            return;
        }

        Reference2ObjectMap<Slot, ?> ingredients = ((GhostSlotsAccessor) ghostSlots).getIngredients();
        Object ghostSlot = ingredients.get(slot);
        if (ghostSlot == null) {
            return;
        }

        ItemStack ghostStack = getGhostItemStack(ghostSlot);
        if (ghostStack == null || ghostStack.isEmpty()) {
            return;
        }

        if (ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING, event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(ItemViewCompat.openRecipeView(ghostStack));
        } else if (ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING, event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(ItemViewCompat.openUsageView(ghostStack));
        }
    }

    /**
     * Reflectively extract the first ItemStack from a {@code GhostSlots.GhostSlot}
     * record, which is package-private and cannot be referenced directly.
     */
    @Unique
    private static ItemStack getGhostItemStack(Object ghostSlot) {
        try {
            java.lang.reflect.Method getItem = ghostSlot.getClass().getMethod("getItem", int.class);
            return (ItemStack) getItem.invoke(ghostSlot, 0);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
