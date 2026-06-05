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

    @Inject(method = "getOrderedRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$includePartialInOrderedRecipes(CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        if (!BetterRecipeBook.config.partialCraftableEqualsCraftable) return;

        List<RecipeHolder<?>> partials = PartialCraftingUtil.getPartiallyCraftableRecipes(this.collection);
        if (partials.isEmpty()) return;

        List<RecipeHolder<?>> result = new ArrayList<>(cir.getReturnValue());
        Set<ResourceLocation> existing = new HashSet<>();
        for (RecipeHolder<?> r : result) {
            existing.add(r.id());
        }
        for (RecipeHolder<?> p : partials) {
            if (!existing.contains(p.id())) {
                result.add(p);
            }
        }
        cir.setReturnValue(result);
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
