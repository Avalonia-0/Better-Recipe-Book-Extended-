package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Safety mixin for RecipeButton to guard against ArithmeticException (/ by zero)
 * when the ordered recipe list is empty.
 * <p>
 * This can happen when RBIP (Recipe Book is Pain) adds creative tabs that have
 * no matching recipes, or in other edge cases where a recipe collection is empty.
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonSafetyMixin {

    @Shadow
    public abstract RecipeDisplayId getCurrentRecipe();

    /**
     * Catches ArithmeticException in getDisplayStack() caused by empty recipe lists.
     * The division by zero happens when getOrderedRecipes() returns an empty list
     * and the code tries to calculate index % 0.
     */
    @Inject(method = "getDisplayStack", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$safeGetDisplayStack(CallbackInfoReturnable<ItemStack> cir) {
        try {
            // Probe: getCurrentRecipe() will throw ArithmeticException if the
            // ordered recipe list is empty (same root cause as getDisplayStack)
            RecipeDisplayId id = this.getCurrentRecipe();
            if (id == null) {
                cir.setReturnValue(ItemStack.EMPTY);
            }
        } catch (ArithmeticException e) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
