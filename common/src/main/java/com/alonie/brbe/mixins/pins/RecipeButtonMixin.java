package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.gui.GuiGraphics;
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

    @Inject(method = "renderWidget", at = @At(value = "RETURN", target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    public void renderWidget_renderFakeItem(GuiGraphics gui, int x, int y, float delta, CallbackInfo ci) {
        // if pins are enabled, and the recipe is pinned, blit the pin texture after the recipe collection is rendered
        // 仅"全 pin 组"（含 Stage 6 生成的副本组）显示 pin 贴图；部分 pin 的
        // 原组不显示（其 pin 变体已被剥离为独立副本组）。
        if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(getCollection())) {
            gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE, getX() - 4, getY() - 4, 32, 32);
        }
    }

}
