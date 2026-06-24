package com.alonie.brbe.mixins.instantcraft;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.Minecraft;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.GuiGraphics;

import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.input.MouseButtonEvent;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.network.chat.Component;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow protected Minecraft minecraft;
    @Shadow private int height;
    @Shadow private int width;
    @Shadow private int xOffset;
    @Shadow public abstract boolean isVisible();

    @Unique protected StateSwitchingButton brbe$instantCraftButton;
    @Unique private static final Component TOGGLE_INSTANT_CRAFT_ON_TEXT = Component.translatable("brbe.gui.instantCraft.on");
    @Unique private static final Component TOGGLE_INSTANT_CRAFT_OFF_TEXT = Component.translatable("brbe.gui.instantCraft.off");

    @Unique
    private boolean brbe$shouldSkip() {
        if (!BetterRecipeBook.config.instantCraft.showButton) {
            return true;
        }

        return ((Object) this) instanceof FurnaceRecipeBookComponent;
    }

    @Inject(method = "initVisuals", at = @At("RETURN"))
    public void reset(CallbackInfo ci) {
        if (brbe$shouldSkip()) {
            return;
        }

        int i = (this.width - 147) / 2 - this.xOffset;
        int j = (this.height - 166) / 2;

        this.brbe$instantCraftButton = new StateSwitchingButton(i + 110, j + 137, 26, 18, false);
        this.brbe$instantCraftButton.useStateTriggeredForTexture(true);
        this.brbe$instantCraftButton.setStateTriggered(BetterRecipeBook.instantCraftingManager.isEnabled());
        this.brbe$instantCraftButton.initTextureValues(BRBTextures.RECIPE_BOOK_INSTANT_CRAFT_BUTTON_SPRITES);
        BetterRecipeBook.instantCraftingManager.lastInstantCraftButton = this.brbe$instantCraftButton;
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (brbe$shouldSkip() || this.brbe$instantCraftButton == null) {
            return;
        }

        this.brbe$instantCraftButton.setStateTriggered(BetterRecipeBook.instantCraftingManager.isEnabled());
        this.brbe$instantCraftButton.visible = this.isVisible();
        if (!this.isVisible()) {
            return;
        }

        this.brbe$instantCraftButton.render(gui, mouseX, mouseY, delta);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible() || brbe$shouldSkip() || this.brbe$instantCraftButton == null) {
            return;
        }

        if (this.brbe$instantCraftButton.mouseClicked(event.x(), event.y(), event.button())) {
            boolean enabled = BetterRecipeBook.instantCraftingManager.toggleEnabled();
            this.brbe$instantCraftButton.setStateTriggered(enabled);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    public void drawTooltip(GuiGraphics gui, int mouseX, int mouseY, Slot hoveredSlot, CallbackInfo ci) {
        if (!this.isVisible() || brbe$shouldSkip() || this.brbe$instantCraftButton == null) {
            return;
        }

        if (this.brbe$instantCraftButton.isHoveredOrFocused() && this.minecraft.screen != null) {
            Component text = this.brbe$instantCraftButton.isStateTriggered() ? TOGGLE_INSTANT_CRAFT_ON_TEXT : TOGGLE_INSTANT_CRAFT_OFF_TEXT;
            ClientCompat.setComponentTooltipForNextFrame(gui, java.util.List.of(text), mouseX, mouseY);
        }
    }
}
