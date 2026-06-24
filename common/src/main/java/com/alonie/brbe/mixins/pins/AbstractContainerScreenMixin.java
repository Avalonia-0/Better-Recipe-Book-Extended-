package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "mouseClicked", at = @At(value = "HEAD"), cancellable = true)
    public void brbe$clickVisibleOverlayFirst(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookComponent<?> book = this.recipeBookComponent;
        if (!book.isVisible()) {
            return;
        }

        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).getRecipeBookPage()).getOverlay();
        if (alternatesWidget.isVisible() && book.mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At(value = "RETURN"))
    public void brbe$renderVisibleOverlayOnTop(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        RecipeBookComponent<?> book = this.recipeBookComponent;
        if (!book.isVisible()) {
            return;
        }

        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).getRecipeBookPage()).getOverlay();
        if (alternatesWidget.isVisible()) {
            guiGraphics.nextStratum();
            alternatesWidget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "HEAD"), cancellable = true)
    public void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        RecipeBookComponent<?> book = this.recipeBookComponent;

        if (!book.isVisible()) return;

        RecipeBookPage page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) page).getOverlay();
        EditBox searchBox = ((RecipeBookComponentAccessor) book).getSearchBox();

        // when F is pressed, handle pinning/unpinning of recipes except when searchBox is consuming input
        if (BetterRecipeBook.config.enablePinning && ClientCompat.matches(BetterRecipeBook.PIN_MAPPING, event.key(), event.scancode(), event.modifiers()) && (searchBox == null || !searchBox.canConsumeInput())) {
            if (alternatesWidget.isVisible()) {
                for (AbstractWidget button : ((OverlayRecipeComponentAccessor) alternatesWidget).getRecipeButtons()) {
                    if (button.isHoveredOrFocused()) {
                        BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavourite(PinnableRecipeCollection.of(alternatesWidget.getRecipeCollection()));
                        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
                        boolean filtering = accessor.isFilteringInvoker();
                        accessor.updateCollectionsInvoker(false, filtering);
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }

            for (RecipeButton button : ((RecipeBookPageAccessor) page).getButtons()) {
                if (button.isHoveredOrFocused()) {
                    BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavourite(PinnableRecipeCollection.of(button.getCollection()));
                    RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
                    boolean filtering = accessor.isFilteringInvoker();
                    accessor.updateCollectionsInvoker(false, filtering);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // when <chat key> is pressed, focus recipes component for searchBox
        // this also works for BrewingRecipeBookComponent as the super's searchBox is set to the same object
        if (ClientCompat.matches(minecraft.options.keyChat, event.key(), event.scancode(), event.modifiers())) {
            minecraft.gui.screen().setFocused(book);
        }

    }

}
