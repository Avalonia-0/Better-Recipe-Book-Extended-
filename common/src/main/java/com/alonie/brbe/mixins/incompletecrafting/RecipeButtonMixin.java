package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin extends AbstractWidget {

    protected RecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }
    @Shadow
    private RecipeCollection collection;

    @Shadow
    public abstract RecipeDisplayId getCurrentRecipe();

    /**
     * Supports the hasCraftable check in renderWidget — treat partially-craftable
     * recipes as craftable for sprite display purposes.
     */
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;"))
    private List<RecipeDisplayEntry> betterRecipeBook$getSelectedRecipes(RecipeCollection collection, RecipeCollection.CraftableStatus status, RecipeCollection originalCollection, boolean filteringCraftable, RecipeBookPage recipeBookPage, ContextMap contextMap) {
        List<RecipeDisplayEntry> result;
        if (BetterRecipeBook.config.showAllRecipesInSurvival
                && status == RecipeCollection.CraftableStatus.CRAFTABLE
                && !filteringCraftable
                && Minecraft.getInstance().screen instanceof InventoryScreen) {
            // When not filtering but "CRAFTABLE" was requested, show ALL recipes
            // (the button init was called with CRAFTABLE but filtering is off)
            List<RecipeDisplayEntry> any = PartialCraftingUtil.getSelectedRecipes(collection, RecipeCollection.CraftableStatus.ANY);
            result = any.stream()
                    .filter(e -> !e.resultItems(contextMap).isEmpty())
                    .toList();
        } else {
            result = PartialCraftingUtil.getSelectedRecipes(collection, status);
        }

        // Reorder: craftable first, then partial, then others (for cycling display).
        // Partial check must precede isCraftable because partial recipes are
        // added to the craftable set via RecipeCollectionAccessor.
        List<RecipeDisplayEntry> reordered = new ArrayList<>(result.size());
        for (RecipeDisplayEntry e : result) {
            RecipeDisplayId id = e.id();
            if (collection.isCraftable(id) && !PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                reordered.add(e);
            }
        }
        for (RecipeDisplayEntry e : result) {
            if (PartialCraftingUtil.isPartiallyCraftable(collection, e.id())) {
                reordered.add(e);
            }
        }
        for (RecipeDisplayEntry e : result) {
            RecipeDisplayId id = e.id();
            if (!collection.isCraftable(id) && !PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                reordered.add(e);
            }
        }
        return reordered;
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;hasCraftable()Z"))
    private boolean betterRecipeBook$renderCurrentRecipeCraftability(RecipeCollection collection, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        try {
            RecipeDisplayId currentRecipe = this.getCurrentRecipe();
            return collection.isCraftable(currentRecipe) || PartialCraftingUtil.isPartiallyCraftable(collection, currentRecipe);
        } catch (ArithmeticException e) {
            return collection.hasCraftable();
        }
    }

    @Inject(method = "isOnlyOption", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$allowNestedAlternativeOverlay(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !PartialCraftingUtil.wasCheckedForPartialMaterials(this.collection)) {
            return;
        }

        if (this.collection.getSelectedRecipes(RecipeCollection.CraftableStatus.ANY).size() > 1
                && (this.collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(this.collection))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V", shift = At.Shift.BEFORE))
    private void betterRecipeBook$renderPartialOverlay(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        RecipeDisplayId currentRecipe;
        try {
            currentRecipe = this.getCurrentRecipe();
        } catch (ArithmeticException e) {
            return;
        }
        if (currentRecipe == null) return;

        if (PartialCraftingUtil.isPartiallyCraftable(this.collection, currentRecipe)) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }
    }
}
