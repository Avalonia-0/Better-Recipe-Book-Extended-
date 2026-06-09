package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Shadow private RecipeCollection collection;
    @Shadow public abstract RecipeDisplayId getCurrentRecipe();

    @Inject(method = "getTooltipText", at = @At("RETURN"))
    private void betterRecipeBook$appendIncompatibleWarning(
            ItemStack itemStack,
            CallbackInfoReturnable<List<Component>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<Component> tooltip = cir.getReturnValue();
        if (tooltip == null || tooltip.isEmpty()) return;

        RecipeDisplayId currentRecipe;
        try { currentRecipe = this.getCurrentRecipe(); }
        catch (ArithmeticException e) { return; }
        if (currentRecipe == null) return;

        if (IncompatibleCraftingUtil.isIncompatible(this.collection, currentRecipe)) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("brbe.gui.environmentIncompatible")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
