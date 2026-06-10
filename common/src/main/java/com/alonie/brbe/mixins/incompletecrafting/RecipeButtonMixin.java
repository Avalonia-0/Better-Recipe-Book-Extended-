package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private List<?> selectedEntries;

    @Shadow
    private boolean allRecipesHaveSameResultDisplay;

    @Shadow
    public abstract RecipeDisplayId getCurrentRecipe();

    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;"))
    private List<RecipeDisplayEntry> betterRecipeBook$getSelectedRecipes(
            RecipeCollection collection, RecipeCollection.CraftableStatus status,
            RecipeCollection originalCollection, boolean filteringCraftable,
            RecipeBookPage recipeBookPage, ContextMap contextMap) {
        // In the 2x2 inventory grid when NOT filtering, show ALL selected recipes,
        // filtering out any with unresolvable results (would render as air).
        List<RecipeDisplayEntry> result;
        if (BetterRecipeBook.config.showAllRecipesInSurvival
                && status == RecipeCollection.CraftableStatus.CRAFTABLE
                && !filteringCraftable
                && Minecraft.getInstance().screen instanceof InventoryScreen) {
            List<RecipeDisplayEntry> any = PartialCraftingUtil.getSelectedRecipes(collection, RecipeCollection.CraftableStatus.ANY);
            result = any.stream()
                    .filter(e -> !e.resultItems(contextMap).isEmpty())
                    .toList();
        } else {
            result = PartialCraftingUtil.getSelectedRecipes(collection, status);
        }

        // Filter: when the collection has craftable or partially-craftable
        // recipes, drop the completely uncraftable ones from cycling so the
        // button only shows craftable + partial alternatives.
        if (result.size() > 1) {
            boolean hasAnyCraftable = collection.hasCraftable();
            boolean hasPartial = PartialCraftingUtil.hasPartialMaterials(collection);

            if (hasAnyCraftable || hasPartial) {
                List<RecipeDisplayEntry> filtered = new ArrayList<>(result.size());
                for (RecipeDisplayEntry e : result) {
                    RecipeDisplayId id = e.id();
                    if (collection.isCraftable(id) && !PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        filtered.add(e);
                    }
                }
                for (RecipeDisplayEntry e : result) {
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, e.id())) {
                        filtered.add(e);
                    }
                }
                return filtered;
            }
        }

        return result;
    }

    @Redirect(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;hasCraftable()Z"))
    private boolean betterRecipeBook$renderCurrentRecipeCraftability(RecipeCollection collection, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Guard against MC-26.1.2 vanilla bug: getCurrentRecipe divides by zero
        try {
            RecipeDisplayId currentRecipe = this.getCurrentRecipe();
            boolean craftable = collection.isCraftable(currentRecipe);
            boolean partial = PartialCraftingUtil.isPartiallyCraftable(collection, currentRecipe);
            return craftable || partial;
        } catch (ArithmeticException e) {
            return collection.hasCraftable();
        }
    }

    @Inject(method = "isOnlyOption", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$allowNestedAlternativeOverlay(CallbackInfoReturnable<Boolean> cir) {
        // Already returning false (multi-option) — nothing to fix.
        if (!cir.getReturnValue()) {
            return;
        }

        // Our filter may have reduced selectedEntries to a single entry,
        // but the collection still contains multiple alternatives.
        // Force isOnlyOption=false so the overlay can show them all.
        if (this.collection.getSelectedRecipes(RecipeCollection.CraftableStatus.ANY).size() > 1
                && (this.collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(this.collection))) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Fixes hasMultipleRecipes() when our filter reduces selectedEntries
     * to a single entry.  The vanilla method checks selectedEntries.size(),
     * which causes the multi-recipe sprite and "Right-click for more info"
     * tooltip to disappear even though the underlying collection has many
     * alternatives.
     */
    @Inject(method = "hasMultipleRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$restoreMultiRecipeIndicator(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return; // Already reporting multiple — nothing to fix.
        }

        // selectedEntries has only 1 entry, but the collection may have more.
        if (this.collection.getSelectedRecipes(RecipeCollection.CraftableStatus.ANY).size() > 1
                && (this.collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(this.collection))) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Prevents the vanilla "stacking" render effect when hasMultipleRecipes()
     * is true but selectedEntries has only 1 entry.
     *
     * Vanilla extractWidgetRenderState renders the item at two offset positions
     * to create a visual stack when {@code hasMultipleRecipes() && allRecipesHaveSameResultDisplay}.
     * With our filter trimming the list to 1 entry this double-render causes
     * the same item to appear overlapped with itself.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void betterRecipeBook$suppressStackingWithSingleEntry(
            RecipeCollection collection, boolean filteringCraftable,
            RecipeBookPage recipeBookPage, ContextMap contextMap, CallbackInfo ci) {
        if (this.allRecipesHaveSameResultDisplay
                && this.selectedEntries != null
                && this.selectedEntries.size() == 1
                && collection.getSelectedRecipes(RecipeCollection.CraftableStatus.ANY).size() > 1) {
            this.allRecipesHaveSameResultDisplay = false;
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;II)V", shift = At.Shift.BEFORE))
    private void betterRecipeBook$renderPartialOverlay(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
