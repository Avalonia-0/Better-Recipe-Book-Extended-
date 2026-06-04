package com.alonie.brbe.mixins.settings;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.interfaces.ISettingsButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements ISettingsButton {

    @Shadow
    protected Minecraft minecraft;

    @Shadow
    private int height;
    @Shadow
    private int width;
    @Shadow
    private int xOffset;

    @Shadow
    public abstract boolean isVisible();

    @Unique
    protected ImageButton _$settingsButton;

    @Inject(method = "initVisuals", at = @At("RETURN"))
    public void reset(CallbackInfo ci) {
        if (BetterRecipeBook.config.settingsButton) {
            int i = (this.width - 147) / 2 - this.xOffset;
            int j = (this.height - 166) / 2;

            this._$settingsButton = createSettingsButton(i, j);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible()) {
            return;
        }

        if (this.settingsButtonMouseClicked(this._$settingsButton, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this._$settingsButton != null) {
            this._$settingsButton.visible = this.isVisible() && BetterRecipeBook.config.settingsButton;
        }

        if (!this.isVisible()) {
            return;
        }

        this.renderSettingsButton(this._$settingsButton, gui, mouseX, mouseY, delta);
    }

    @Inject(method = "extractTooltip", at = @At("RETURN"))
    public void drawTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY, Slot hoveredSlot, CallbackInfo ci) {
        if (!this.isVisible()) {
            return;
        }

        this.renderSettingsButtonTooltip(this._$settingsButton, gui, mouseX, mouseY);
    }
}
