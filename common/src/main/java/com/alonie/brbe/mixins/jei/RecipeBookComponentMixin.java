package com.alonie.brbe.mixins.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.BetterRecipeBook;
import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.mojang.blaze3d.platform.InputConstants;
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
    private Slot brbe$hoveredSlot;

    /**
     * Capture the hovered slot during tooltip extraction so it is available
     * in {@code keyPressed} for ghost-item lookup.
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"))
    private void brbe$captureHoveredSlot(GuiGraphicsExtractor gui, int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        this.brbe$hoveredSlot = slot;
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void brbe$handleJeiKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }

        // Trigger retry if handler not set yet (e.g. REI loaded after mod init)
        if (!ItemViewCompat.isLoaded()) {
            ReiCompat.isLoaded();  // calls ensureRegistered() → retries register()
        }
        if (!ItemViewCompat.isLoaded()) {
            return;
        }

        int keyCode = event.key();
        int scanCode = event.scancode();

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
                if (ItemViewCompat.matchesShowRecipe(keyCode, scanCode)) {
                    cir.setReturnValue(ItemViewCompat.openRecipeView(hoveredStack));
                } else if (ItemViewCompat.matchesShowUses(keyCode, scanCode)) {
                    cir.setReturnValue(ItemViewCompat.openUsageView(hoveredStack));
                }
            }
            return;
        }

        // ── 2. Ghost items ─────────────────────────────────────────────
        Slot slot = this.brbe$hoveredSlot;
        if (slot == null) {
            return;
        }

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

        if (ItemViewCompat.matchesShowRecipe(keyCode, scanCode)) {
            cir.setReturnValue(ItemViewCompat.openRecipeView(ghostStack));
        } else if (ItemViewCompat.matchesShowUses(keyCode, scanCode)) {
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
            // GhostSlot is a Record(List<ItemStack> items, boolean isResultSlot).
            // At runtime the method name is mapping-dependent.  Iterate over
            // public no-arg methods to find the one that returns List<ItemStack>.
            for (java.lang.reflect.Method m : ghostSlot.getClass().getMethods()) {
                if (m.getReturnType() == java.util.List.class && m.getParameterCount() == 0) {
                    @SuppressWarnings("unchecked")
                    java.util.List<ItemStack> items = (java.util.List<ItemStack>) m.invoke(ghostSlot);
                    if (items != null && !items.isEmpty()) {
                        ItemStack first = items.get(0);
                        if (first != null && !first.isEmpty()) return first;
                    }
                }
            }
            return ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
