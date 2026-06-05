package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {
    @Shadow
    private RecipeCollection collection;

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;hasCraftable()Z"))
    private boolean betterRecipeBook$renderPartiallyCraftableAsCraftable(RecipeCollection collection, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        return collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(collection);
    }

    @Redirect(method = "getOrderedRecipes", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getRecipes(Z)Ljava/util/List;"))
    private List<RecipeHolder<?>> betterRecipeBook$includePartialInGetRecipes(RecipeCollection collection, boolean craftable) {
        List<RecipeHolder<?>> result = new ArrayList<>(collection.getRecipes(craftable));
        if (craftable && BetterRecipeBook.config.partialCraftableEqualsCraftable) {
            Set<ResourceLocation> existingIds = new HashSet<>();
            for (RecipeHolder<?> recipe : result) {
                existingIds.add(recipe.id());
            }
            for (RecipeHolder<?> partial : PartialCraftingUtil.getPartiallyCraftableRecipes(collection)) {
                if (!existingIds.contains(partial.id())) {
                    result.add(partial);
                }
            }
        }
        return result;
    }

    @Inject(method = "isOnlyOption", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$allowNestedAlternativeOverlay(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !PartialCraftingUtil.wasCheckedForPartialMaterials(this.collection)) {
            return;
        }

        if (this.collection.getRecipes().size() > 1
                && (this.collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(this.collection))) {
            cir.setReturnValue(false);
        }
    }
}
