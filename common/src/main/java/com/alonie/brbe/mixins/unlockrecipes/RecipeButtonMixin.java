package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.RecipeUnlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
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
    @Shadow
    public abstract RecipeDisplayId getCurrentRecipe();

    @Inject(method = "getTooltipText", at = @At("RETURN"))
    public void getTooltip(ItemStack itemStack, CallbackInfoReturnable<List<Component>> cir) {
        // Don't show "craft once to unlock" when unlock-all is enabled
        if (BetterRecipeBook.config.newRecipes.unlockAll) {
            return;
        }

        if (RecipeUnlockUtil.isTemporarilyUnlocked(this.getCurrentRecipe())) {
            cir.getReturnValue().add(0, Component.translatable("brbe.gui.crafting.lockedRecipe").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
