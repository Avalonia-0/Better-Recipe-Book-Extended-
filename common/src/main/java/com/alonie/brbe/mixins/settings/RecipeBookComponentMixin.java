package com.alonie.brbe.mixins.settings;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ConfigTipsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
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
 * second ISettingsButton from BRBE causes an IncompatibleClassChangeError
 * because the JVM can't resolve conflicting default method implementations.
 * Instead, the settings button logic is inlined here and delegates to BRB's
 * ISettingsButton where applicable.
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

    @Unique
    protected ImageButton _$settingsButton;

    @Inject(method = "initVisuals", at = @At("RETURN"))
    public void reset(CallbackInfo ci) {
        if (BetterRecipeBook.ctx().config().settingsButton) {
            int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
            int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2 + BookLayout.settingsY();
            this._$settingsButton = new ImageButton(i + BookLayout.SETTINGS_X_OFFSET, j,
                    BookLayout.settingsSize(), BookLayout.settingsSize(),
                    BRBTextures.SETTINGS_BUTTON_SPRITES, button -> {
                ConfigTipsHelper.openConfigScreen(BrbeConfig.class, Minecraft.getInstance().screen);
            });
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    public void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this._$settingsButton != null && BetterRecipeBook.ctx().config().settingsButton
                && this._$settingsButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;render(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this._$settingsButton != null && BetterRecipeBook.ctx().config().settingsButton) {
            this._$settingsButton.render(gui, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "renderTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;renderGhostRecipeTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"))
    public void drawTooltip(GuiGraphics gui, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        if (this._$settingsButton != null && this._$settingsButton.isHoveredOrFocused()
                && BetterRecipeBook.ctx().config().settingsButton && Minecraft.getInstance().screen != null) {
            gui.renderTooltip(Minecraft.getInstance().font,
                    net.minecraft.network.chat.Component.translatable("brb.gui.settings.open"), mouseX, mouseY);
        }
    }
}
