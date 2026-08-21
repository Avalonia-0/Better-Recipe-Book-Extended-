package com.alonie.brbe.mixins.rei;

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
    private Slot brbe$hoveredSlot;

    /**
     * Capture the hovered slot during tooltip rendering so it is available
     * in {@code keyPressed} for ghost-item lookup.
     */
    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void brbe$captureHoveredSlot(GuiGraphics gui, int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
        this.brbe$hoveredSlot = slot;
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void brbe$handleReiKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Trigger retry if handler not set yet (e.g. REI loaded after mod init)
        if (!ItemViewCompat.isLoaded()) {
            ReiCompat.isLoaded();  // calls ensureRegistered() → retries register()
        }
        if (!ItemViewCompat.isLoaded()) {
            return;
        }

        if (cir.getReturnValueZ()) {
            return;
        }

        int keyCode = event.key();
        int scanCode = event.scancode();

        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) this;
        RecipeBookPage page = accessor.getRecipeBookPage();
        if (page == null) return;

        // ── 1. Recipe buttons ──────────────────────────────────────────
        for (RecipeButton button : RecipeBookPageAccessor.class.cast(page).getButtons()) {
            if (button.isHoveredOrFocused()) {
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
        }

        // ── 2. Ghost items ─────────────────────────────────────────────
        Slot slot = this.brbe$hoveredSlot;
        if (slot == null) return;

        GhostSlots ghostSlots = accessor.getGhostSlots();
        if (ghostSlots == null) return;

        Reference2ObjectMap<Slot, ?> ingredients = ((GhostSlotsAccessor) ghostSlots).getIngredients();
        if (ingredients == null) return;

        Object ghostSlot = ingredients.get(slot);
        if (ghostSlot == null) {
            BetterRecipeBook.LOGGER.info("[BRBE] Ghost path: no ghost in ingredients for slot#{} (map size={})",
                    slot.index, ingredients.size());
            return;
        }

        ItemStack ghostStack = getGhostItemStack(ghostSlot);
        if (ghostStack == null || ghostStack.isEmpty()) {
            BetterRecipeBook.LOGGER.info("[BRBE] Ghost path: getGhostItemStack returned empty for slot#{}", slot.index);
            return;
        }

        BetterRecipeBook.LOGGER.info("[BRBE] Ghost path: querying {} for slot#{}",
                ghostStack.getHoverName().getString(), slot.index);
        if (ItemViewCompat.matchesShowRecipe(keyCode, scanCode)) {
            cir.setReturnValue(ItemViewCompat.openRecipeView(ghostStack));
        } else if (ItemViewCompat.matchesShowUses(keyCode, scanCode)) {
            cir.setReturnValue(ItemViewCompat.openUsageView(ghostStack));
        }
    }

    @Unique
    private static ItemStack getGhostItemStack(Object ghostSlot) {
        try {
            // GhostSlot is a Record(List<ItemStack> items, boolean isResultSlot).
            // At runtime the method name is mapping-dependent ("items" in Mojang,
            // an intermediary name in Yarn).  Iterate over public no-arg methods
            // to find the one that returns List<ItemStack>.
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
            BetterRecipeBook.LOGGER.warn("[BRBE] getGhostItemStack failed for {}", ghostSlot.getClass().getName(), e);
            return ItemStack.EMPTY;
        }
    }
}
