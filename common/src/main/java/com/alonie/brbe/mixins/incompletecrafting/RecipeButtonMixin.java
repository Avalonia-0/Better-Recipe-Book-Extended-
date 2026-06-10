package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
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
     * Reorders the result of getSelectedRecipes so that the button cycles
     * through craftable recipes first, then partially-craftable recipes,
     * then any remaining alternatives.
     *
     * Uses full-method descriptor to ensure unambiguous Mixin matching.
     * The enclosing init() params (filteringCraftable, page, contextMap) are
     * NOT captured because they are not needed for reordering.
     */
    @Redirect(
        method = "init(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;ZLnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;Lnet/minecraft/util/context/ContextMap;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;")
    )
    private List<RecipeDisplayEntry> betterRecipeBook$reorderSelectedRecipes(RecipeCollection collection, RecipeCollection.CraftableStatus status) {
        // Collect the entries to reorder. At this point status is always ANY
        // because DisableCraftableFilter makes isFiltering() always return false.
        List<RecipeDisplayEntry> result = collection.getSelectedRecipes(status);

        if (result.size() <= 1) {
            return result;
        }

        // Three-pass stable reorder: fully-craftable → partial → rest
        List<RecipeDisplayEntry> reordered = new ArrayList<>(result.size());

        // Pass 1: recipes that are craftable and NOT partially-craftable
        for (RecipeDisplayEntry e : result) {
            RecipeDisplayId id = e.id();
            if (collection.isCraftable(id) && !PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                reordered.add(e);
            }
        }

        // Pass 2: recipes that ARE partially-craftable
        for (RecipeDisplayEntry e : result) {
            if (PartialCraftingUtil.isPartiallyCraftable(collection, e.id())) {
                reordered.add(e);
            }
        }

        // Pass 3: everything else (neither craftable nor partial)
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
