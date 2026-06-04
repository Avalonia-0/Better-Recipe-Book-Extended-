package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.KeyMappingAccessor;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin extends AbstractWidget {

    protected RecipeButtonMixin(int i, int j, int k, int l, Component component) {
        super(i, j, k, l, component);
    }

    @Shadow
    public abstract RecipeCollection getCollection();

    @Inject(method = "getTooltipText", at = @At("RETURN"))
    public void getTooltip(CallbackInfoReturnable<List<Component>> cir) {
        if (!BetterRecipeBook.config.enablePinning) return;

        List<Component> list = cir.getReturnValue();
        if (list == null) {
            return;
        }

        list.add(Component.empty());

        String keyName = ((KeyMappingAccessor) BetterRecipeBook.PIN_MAPPING).getKey().getDisplayName().getString();
        if (BetterRecipeBook.pinnedRecipeManager.has(PinnableRecipeCollection.of(this.getCollection()))) {
            list.add(Component.translatable("brbe.gui.pin.remove", keyName));
        } else {
            list.add(Component.translatable("brbe.gui.pin.add", keyName));
        }
    }

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    public void renderWidget_renderFakeItem(GuiGraphicsExtractor gui, int x, int y, float delta, CallbackInfo ci) {
        // if pins are enabled, and the recipe is pinned, blit the pin texture after the recipe collection is rendered
        if (BetterRecipeBook.config.enablePinning && BetterRecipeBook.pinnedRecipeManager.has(PinnableRecipeCollection.of(getCollection()))) {
            ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE, getX() - 4, getY() - 4, 32, 32);
        }
    }

}
