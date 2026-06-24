package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Shadow private RecipeCollection collection;
    @Shadow public abstract RecipeHolder<?> getRecipe();

    @Inject(method = "getTooltipText", at = @At("RETURN"))
    private void brbe$appendIncompatibleWarning(
            CallbackInfoReturnable<List<Component>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<Component> list = cir.getReturnValue();
        if (list == null || list.isEmpty()) return;

        RecipeHolder<?> current;
        try {
            current = this.getRecipe();
        } catch (ArithmeticException e) {
            return;
        }
        if (current == null) return;

        if (IncompatibleCraftingUtil.checkIncompatible(this.collection, current.id())) {
            list.add(Component.empty());
            list.add(Component.translatable("brbe.gui.environmentIncompatible")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
