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
import net.minecraft.world.item.crafting.ShapedRecipe;
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
    @Shadow private List<RecipeHolder<?>> recipes;

    /**
     * On the inventory (2×2) screen, append recipes that require a 3×3
     * crafting grid to the button's recipe list so they appear in the
     * recipe book alongside compatible recipes.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void betterRecipeBook$appendIncompatibleRecipes(
            RecipeCollection collection, boolean isFiltering,
            net.minecraft.client.gui.screens.recipebook.RecipeBookPage page,
            CallbackInfo ci) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (this.recipes == null) return;

        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (holder.value() instanceof ShapedRecipe shaped
                    && (shaped.getWidth() > 2 || shaped.getHeight() > 2)) {
                if (!this.recipes.contains(holder)) {
                    if (extras == null) extras = new ArrayList<>();
                    extras.add(holder);
                }
            }
        }

        if (extras != null) {
            List<RecipeHolder<?>> combined = new ArrayList<>(this.recipes);
            combined.addAll(extras);
            this.recipes = combined;
        }
    }

    @Inject(method = "getTooltipText", locals = LocalCapture.CAPTURE_FAILHARD, at = @At("RETURN"))
    private void betterRecipeBook$appendIncompatibleWarning(
            CallbackInfoReturnable<List<Component>> cir, ItemStack itemStack, List<Component> list) {
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;
        if (list == null || list.isEmpty()) return;

        List<RecipeHolder<?>> recipeList = this.collection.getRecipes();
        if (recipeList.isEmpty()) return;

        ResourceLocation currentId = recipeList.get(this.currentIndex % recipeList.size()).id();
        if (IncompatibleCraftingUtil.isIncompatible(this.collection, currentId)) {
            list.add(Component.empty());
            list.add(
                Component.translatable("brbe.gui.environmentIncompatible")
                    .withStyle(ChatFormatting.RED)
            );
        }
    }
}
