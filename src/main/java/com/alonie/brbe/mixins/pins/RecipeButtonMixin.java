package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin extends AbstractWidget {

    protected RecipeButtonMixin(int i, int j, int k, int l, Component component) {
        super(i, j, k, l, component);
    }

    @Shadow
    public abstract RecipeCollection getCollection();

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    public void renderWidget_renderFakeItem(GuiGraphicsExtractor gui, int x, int y, float delta, CallbackInfo ci) {
        // if pins are enabled, and the recipe is pinned, blit the pin texture after the recipe collection is rendered
        if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(PinnableRecipeCollection.of(getCollection()))) {
            ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE, getX() - 4, getY() - 4, 32, 32);
        }
    }

}
