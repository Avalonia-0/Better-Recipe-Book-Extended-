package com.alonie.brbe.mixins.modname;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.ModNameUtil;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Inject(method = "getTooltipText", at = @At("RETURN"))
    private void betterRecipeBook$appendModName(ItemStack itemStack, CallbackInfoReturnable<List<Component>> cir) {
        if (!BetterRecipeBook.config.showModName) {
            return;
        }

        List<Component> tooltip = cir.getReturnValue();
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }

        Component modName = ModNameUtil.getFormattedModName(itemStack);
        if (modName == null || modName.getString().isEmpty()) {
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(modName);
    }
}
