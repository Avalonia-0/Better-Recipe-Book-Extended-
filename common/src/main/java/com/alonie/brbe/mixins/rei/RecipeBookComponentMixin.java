package com.alonie.brbe.mixins.rei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds REI recipe/usage view shortcuts to the vanilla crafting recipe book.
 * Press R to open recipe view, U to open usage view for the hovered recipe button.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow
    @Final
    protected Minecraft minecraft;

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
    }
}
