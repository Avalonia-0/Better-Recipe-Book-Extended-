package com.alonie.brbe.mixins.incompatibleenvironment;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Shadow private RecipeCollection collection;
    @Shadow private int currentIndex;
    @Shadow
    private List<RecipeHolder<?>> getOrderedRecipes() {
        throw new AssertionError();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void betterRecipeBook$appendIncompatibleRecipes(
            RecipeCollection collection, boolean isFiltering,
            net.minecraft.client.gui.screens.recipebook.RecipeBookPage page,
            CallbackInfo ci) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<RecipeHolder<?>> ordered = this.getOrderedRecipes();
        if (ordered == null) return;

        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (IncompatibleCraftingUtil.checkIncompatible(collection, holder.id())
                    && !ordered.contains(holder)) {
                if (extras == null) extras = new ArrayList<>();
                extras.add(holder);
            }
        }
    }

    @Inject(method = "getTooltipText", locals = LocalCapture.CAPTURE_FAILHARD, at = @At("RETURN"))
    private void betterRecipeBook$appendIncompatibleWarning(
            CallbackInfoReturnable<List<Component>> cir, net.minecraft.world.item.ItemStack itemStack, List<Component> list) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (list == null || list.isEmpty()) return;

        List<RecipeHolder<?>> recipeList = this.collection.getRecipes();
        if (recipeList.isEmpty()) return;

        ResourceLocation currentId = recipeList.get(this.currentIndex % recipeList.size()).id();
        if (IncompatibleCraftingUtil.checkIncompatible(this.collection, currentId)) {
            list.add(Component.empty());
            list.add(Component.translatable("brbe.gui.environmentIncompatible")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
