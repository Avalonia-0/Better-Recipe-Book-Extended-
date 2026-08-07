package com.alonie.brbe.mixins.settings;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.ConfigTipsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NOTE: Does NOT implement ISettingsButton. BRB already injects its own
 * ISettingsButton into RecipeBookComponent (via marsh.town.brb). Adding a
 * second ISettingsButton from BRBE causes an IncompatibleClassChangeError.
 * Settings button logic is inlined here.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

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
        if (BetterRecipeBook.ctx().config().settingsButton) {
            int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
            int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2 + BookLayout.SETTINGS_Y_OFFSET;
            this._$settingsButton = new ImageButton(i + BookLayout.SETTINGS_X_OFFSET, j,
                    BookLayout.SETTINGS_SIZE, BookLayout.SETTINGS_SIZE,
                    BRBTextures.SETTINGS_BUTTON_SPRITES, button ->
                    ConfigTipsHelper.openConfigScreen(Config.class, Minecraft.getInstance().screen));
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible()) return;
        if (this._$settingsButton != null && BetterRecipeBook.ctx().config().settingsButton
                && ClientCompat.mouseClicked(this._$settingsButton, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this._$settingsButton != null) {
            this._$settingsButton.visible = this.isVisible() && BetterRecipeBook.ctx().config().settingsButton;
        }
        if (!this.isVisible()) return;
        if (this._$settingsButton != null && BetterRecipeBook.ctx().config().settingsButton) {
            this._$settingsButton.render(gui, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    public void drawTooltip(GuiGraphics gui, int mouseX, int mouseY, Slot hoveredSlot, CallbackInfo ci) {
        if (!this.isVisible()) return;
        if (this._$settingsButton != null && this._$settingsButton.isHoveredOrFocused()
                && BetterRecipeBook.ctx().config().settingsButton && Minecraft.getInstance().screen != null) {
            ClientCompat.setComponentTooltipForNextFrame(gui,
                    java.util.List.of(Component.translatable("brbe.gui.settings.open")), mouseX, mouseY);
        }
    }
}
