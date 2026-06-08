package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Shadow private RecipeCollection collection;
    @Shadow private int currentIndex;

    @Inject(method = "getTooltipText", locals = LocalCapture.CAPTURE_FAILHARD, at = @At("RETURN"))
    private void betterRecipeBook$appendIncompatibleWarning(
            CallbackInfoReturnable<List<Component>> cir, ItemStack itemStack, List<Component> list) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (list == null || list.isEmpty()) return;

        List<RecipeHolder<?>> recipes = this.collection.getRecipes();
        if (recipes.isEmpty()) return;

        ResourceLocation currentId = recipes.get(this.currentIndex % recipes.size()).id();
        if (IncompatibleCraftingUtil.isIncompatible(this.collection, currentId)) {
            list.add(Component.empty());
            list.add(
                Component.translatable("brbe.gui.environmentIncompatible")
                    .withStyle(ChatFormatting.RED)
            );
        }
    }
}
