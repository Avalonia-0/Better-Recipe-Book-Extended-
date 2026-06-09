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
    @Shadow public abstract RecipeHolder<?> getRecipe();
    @Shadow
    private List<RecipeHolder<?>> getOrderedRecipes() {
        throw new AssertionError();
    }

    @Inject(method = "getOrderedRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$includeIncompatibleRecipes(
            CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) return;
        if (!(Minecraft.getInstance().screen instanceof InventoryScreen)) return;

        List<RecipeHolder<?>> recipes = cir.getReturnValue();
        if (recipes == null) return;

        List<RecipeHolder<?>> extras = null;
        for (RecipeHolder<?> holder : this.collection.getRecipes()) {
            if (IncompatibleCraftingUtil.checkIncompatible(this.collection, holder.id())
                    && !recipes.contains(holder)) {
                if (extras == null) extras = new ArrayList<>();
                extras.add(holder);
            }
        }

        if (extras != null) {
            List<RecipeHolder<?>> combined = new ArrayList<>(recipes);
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
