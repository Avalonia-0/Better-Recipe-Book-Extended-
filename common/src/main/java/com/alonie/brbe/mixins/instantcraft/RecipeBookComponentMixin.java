package com.alonie.brbe.mixins.instantcraft;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    @Unique private static final Logger LOG = LogManager.getLogger("BRBE-InstantCraft");
    @Unique private int brbe$renderFrameCount;
    @Unique private boolean brbe$lastRenderTriggered;
    @Unique private boolean brbe$lastRenderVisible;

    @Unique protected StateSwitchingButton brbe$instantCraftButton;
    @Unique private static final Component TOGGLE_INSTANT_CRAFT_ON_TEXT = Component.translatable("brbe.gui.instantCraft.on");
    @Unique private static final Component TOGGLE_INSTANT_CRAFT_OFF_TEXT = Component.translatable("brbe.gui.instantCraft.off");

    @Unique
    private boolean brbe$shouldSkip() {
        if (!BetterRecipeBook.ctx().config().instantCraft.showButton) {
            return true;
        }

        return ((Object) this) instanceof FurnaceRecipeBookComponent;
    }

    @Inject(method = "initVisuals", at = @At("RETURN"))
    public void reset(CallbackInfo ci) {
        if (brbe$shouldSkip()) {
            LOG.info("[reset] skipped (showButton={})", BetterRecipeBook.ctx().config().instantCraft.showButton);
            return;
        }

        // Compute position relative to book geometry (uses BookLayout constants)
        int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;

        // Right-aligned with grid zone right edge, bottom-aligned with forward arrow
        int btnX = i + BookLayout.TEXTURE_WIDTH - BookLayout.GRID_LEFT_PADDING - 26;
        int btnY = j + BookLayout.SETTINGS_Y_OFFSET + 17 - 18;

        this.brbe$instantCraftButton = new StateSwitchingButton(
                btnX, btnY, 26, 18,
                BetterRecipeBook.instantCraftingManager.isEnabled());
        this.brbe$instantCraftButton.useStateTriggeredForTexture(true);
        this.brbe$instantCraftButton.initTextureValues(BRBTextures.RECIPE_BOOK_INSTANT_CRAFT_BUTTON_SPRITES);
        BetterRecipeBook.instantCraftingManager.lastInstantCraftButton = this.brbe$instantCraftButton;
        brbe$renderFrameCount = 0;
        brbe$lastRenderTriggered = false;
        brbe$lastRenderVisible = false;
        LOG.info("[reset] btn created at ({},{}), isEnabled={}, this.isVisible={}, btn.visible={}",
                btnX, btnY, BetterRecipeBook.instantCraftingManager.isEnabled(),
                this.isVisible(), this.brbe$instantCraftButton.visible);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (brbe$shouldSkip() || this.brbe$instantCraftButton == null) {
            if (this.brbe$instantCraftButton == null && brbe$renderFrameCount == 0) {
                LOG.warn("[render] button is null! shouldSkip={}", brbe$shouldSkip());
            }
            return;
        }

        boolean triggered = BetterRecipeBook.instantCraftingManager.isEnabled();
        boolean bookVisible = this.isVisible();

        this.brbe$instantCraftButton.setStateTriggered(triggered);
        this.brbe$instantCraftButton.visible = bookVisible;
        if (!bookVisible) {
            if (brbe$lastRenderVisible) {
                LOG.info("[render] book became invisible, frame={}", brbe$renderFrameCount);
                brbe$lastRenderVisible = false;
            }
            return;
        }

        // Log on first frame and on state changes (avoid spam)
        boolean stateChanged = (triggered != brbe$lastRenderTriggered) || !bookVisible != !brbe$lastRenderVisible;
        if (brbe$renderFrameCount == 0 || stateChanged || brbe$renderFrameCount % 120 == 0) {
            LOG.info("[render] frame={}, triggered={}, bookVisible={}, btn.visible={}, btnState={}, hovered={}, pos=({},{})",
                    brbe$renderFrameCount, triggered, bookVisible,
                    this.brbe$instantCraftButton.visible,
                    this.brbe$instantCraftButton.isStateTriggered(),
                    this.brbe$instantCraftButton.isHovered(),
                    this.brbe$instantCraftButton.getX(), this.brbe$instantCraftButton.getY());
        }
        brbe$lastRenderTriggered = triggered;
        brbe$lastRenderVisible = bookVisible;

        this.brbe$instantCraftButton.extractRenderState(gui, mouseX, mouseY, delta);
        brbe$renderFrameCount++;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible() || brbe$shouldSkip() || this.brbe$instantCraftButton == null) {
            return;
        }

        if (ClientCompat.mouseClicked(this.brbe$instantCraftButton, event.x(), event.y(), event.button())) {
            boolean enabled = BetterRecipeBook.instantCraftingManager.toggleEnabled();
            this.brbe$instantCraftButton.setStateTriggered(enabled);
            LOG.info("[click] instantCraft toggled -> {}", enabled);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractTooltip", at = @At("RETURN"))
    public void drawTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY, Slot hoveredSlot, CallbackInfo ci) {
        if (!this.isVisible() || brbe$shouldSkip() || this.brbe$instantCraftButton == null) {
            return;
        }

        if (this.brbe$instantCraftButton.isHoveredOrFocused() && this.minecraft.screen != null) {
            Component text = this.brbe$instantCraftButton.isStateTriggered() ? TOGGLE_INSTANT_CRAFT_ON_TEXT : TOGGLE_INSTANT_CRAFT_OFF_TEXT;
            ClientCompat.setComponentTooltipForNextFrame(gui, java.util.List.of(text), mouseX, mouseY);
        }
    }
}
