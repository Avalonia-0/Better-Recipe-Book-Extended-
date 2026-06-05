package com.alonie.brbe.mixins.jei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$handleJeiKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!ItemViewCompat.isLoaded() || cir.getReturnValueZ()) {
            return;
        }

        RecipeBookPage page = ((RecipeBookComponentAccessor) this).getRecipeBookPage();
        if (page == null) {
            return;
        }

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
    }
}
