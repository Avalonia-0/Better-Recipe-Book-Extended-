package com.alonie.brbe.mixins.rei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.ClientCompat;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds REI/JEI recipe/usage view shortcuts to the vanilla crafting recipe book,
 * including ghost items in the crafting grid.
 * Press R to open recipe view, U to open usage view.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Unique
    private Slot betterRecipeBook$hoveredSlot;

    /**
     * Capture the hovered slot during tooltip rendering so it is available
     * in {@code keyPressed} for ghost-item lookup.
     */
    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void betterRecipeBook$captureHoveredSlot(GuiGraphics gui, int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        this.betterRecipeBook$hoveredSlot = slot;
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$handleReiKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!ItemViewCompat.isLoaded()) {
            return;
        }

        if (cir.getReturnValueZ()) {
            return;
        }

        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) this;
        RecipeBookPage page = accessor.getRecipeBookPage();
        if (page == null) return;

        // ── 1. Recipe buttons ──────────────────────────────────────────
        for (RecipeButton button : RecipeBookPageAccessor.class.cast(page).getButtons()) {
            if (button.isHoveredOrFocused()) {
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
        }

        // ── 2. Ghost items ─────────────────────────────────────────────
        Slot slot = this.betterRecipeBook$hoveredSlot;
        if (slot == null) return;

        GhostSlots ghostSlots = accessor.getGhostSlots();
        if (ghostSlots == null) return;

        Reference2ObjectMap<Slot, ?> ingredients = ((GhostSlotsAccessor) ghostSlots).getIngredients();
        if (ingredients == null) return;

        Object ghostSlot = ingredients.get(slot);
        if (ghostSlot == null) return;

        ItemStack ghostStack = getGhostItemStack(ghostSlot);
        if (ghostStack == null || ghostStack.isEmpty()) return;

        if (ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING, event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(ItemViewCompat.openRecipeView(ghostStack));
        } else if (ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING, event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(ItemViewCompat.openUsageView(ghostStack));
        }
    }

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
