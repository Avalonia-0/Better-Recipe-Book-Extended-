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

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Shadow private RecipeCollection collection;

    @Shadow
    private List<RecipeHolder<?>> getOrderedRecipes() {
        throw new AssertionError();
    }

    /**
     * Intercept getOrderedRecipes() return value to append incompatible (3x3) recipes.
     * In 1.21.1, getOrderedRecipes() is a private method on RecipeButton that returns
     * the list of recipes to render. We inject at RETURN to add incompatible recipes
     * that would otherwise be excluded by the 2x2 grid filter.
     */
    @Inject(method = "getOrderedRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$appendIncompatibleRecipes(
            CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<RecipeHolder<?>> ordered = cir.getReturnValue();
        if (ordered == null) return;

        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : this.collection.getRecipes()) {
            if (IncompatibleCraftingUtil.checkIncompatible(this.collection, holder.id())
                    && !ordered.contains(holder)) {
                if (extras == null) extras = new ArrayList<>();
                extras.add(holder);
            }
        }

        if (extras != null) {
            List<RecipeHolder<?>> combined = new ArrayList<>(ordered);
            combined.addAll(extras);
            cir.setReturnValue(combined);
        }
    }

    @Inject(method = "getTooltipText", at = @At("RETURN"))
    private void betterRecipeBook$appendIncompatibleWarning(
            CallbackInfoReturnable<List<Component>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<Component> list = cir.getReturnValue();
        if (list == null || list.isEmpty()) return;

        List<RecipeHolder<?>> ordered = this.getOrderedRecipes();
        if (ordered == null || ordered.isEmpty()) return;

        // Use the first incompatible recipe's tooltip
        for (RecipeHolder<?> holder : ordered) {
            if (IncompatibleCraftingUtil.checkIncompatible(this.collection, holder.id())) {
                list.add(Component.empty());
                list.add(Component.translatable("brbe.gui.environmentIncompatible")
                        .withStyle(ChatFormatting.RED));
                return;
            }
        }
    }
}
