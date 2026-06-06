package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin extends AbstractWidget {

    protected RecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }
    @Shadow
    private RecipeCollection collection;

    @Shadow
    private int currentIndex;

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;hasCraftable()Z"))
    private boolean betterRecipeBook$renderPartiallyCraftableAsCraftable(RecipeCollection collection, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        return collection.hasCraftable()
                || (BetterRecipeBook.config.partialCraftableEqualsCraftable
                        && PartialCraftingUtil.hasPartialMaterials(collection));
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void betterRecipeBook$renderPartialOverlay(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        List<RecipeHolder<?>> recipes = this.collection.getRecipes();
        if (recipes.isEmpty()) return;

        RecipeHolder<?> current = recipes.get(this.currentIndex % recipes.size());
        if (PartialCraftingUtil.isPartiallyCraftable(this.collection, current)) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }
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
