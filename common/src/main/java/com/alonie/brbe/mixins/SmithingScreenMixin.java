package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.interfaces.TopLayerOverlayProvider;
import com.alonie.brbe.smithingtable.SmithingRecipeBookComponent;
import com.alonie.brbe.smithingtable.SmithingRecipeBookPage;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingScreen.class)
public abstract class SmithingScreenMixin extends ItemCombinerScreen<SmithingMenu> implements TopLayerOverlayProvider {
    @Shadow
    protected abstract void updateArmorStandPreview(ItemStack itemStack);

    @Unique
    public final SmithingRecipeBookComponent _$recipeBookComponent = new SmithingRecipeBookComponent();
    @Unique
    private boolean _$widthNarrow;

    public SmithingScreenMixin(SmithingMenu itemCombinerMenu, Inventory inventory, Component component, Identifier Identifier) {
        super(itemCombinerMenu, inventory, component, Identifier);
    }

    @Inject(method = "subInit", at = @At("RETURN"))
    void init(CallbackInfo ci) {
        if (BetterRecipeBook.config.enableBook) {
            this._$widthNarrow = this.width < 379;
            this._$recipeBookComponent.init(this.width, this.height, this.minecraft, _$widthNarrow, this.menu, this::updateArmorStandPreview, Minecraft.getInstance().getConnection().registryAccess());

            if (!BetterRecipeBook.config.keepCentered) {
                this.leftPos = this._$recipeBookComponent.findLeftEdge(this.width, this.imageWidth);
            }

            // NOTE : width and height are both 0
            this.addRenderableWidget(new ImageButton(this.leftPos + 147, this.height / 2 - 75, 20, 18, BRBTextures.RECIPE_BOOK_BUTTON_SPRITES, (button) -> {
                this._$recipeBookComponent.toggleVisibility();
                if (!BetterRecipeBook.config.keepCentered) {
                    this.leftPos = this._$recipeBookComponent.findLeftEdge(this.width, this.imageWidth);
                }
                button.setPosition(this.leftPos + 147, this.height / 2 - 75);
            }));

            this.addWidget(this._$recipeBookComponent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (_$recipeBookComponent.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (_$recipeBookComponent.keyReleased(event)) {
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (_$recipeBookComponent.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.brbe$clickTopLayerOverlay(event, doubleClick)) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean brbe$hasTopLayerOverlay() {
        return this._$recipeBookComponent.isVisible()
                && this._$recipeBookComponent.recipesPage instanceof SmithingRecipeBookPage page
                && page.overlayIsVisible();
    }

    @Override
    public void brbe$renderTopLayerOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.brbe$hasTopLayerOverlay()) {
            ((SmithingRecipeBookPage) this._$recipeBookComponent.recipesPage).overlay.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean brbe$clickTopLayerOverlay(MouseButtonEvent event, boolean doubleClick) {
        if (this.brbe$hasTopLayerOverlay()
                && this._$recipeBookComponent.mouseClicked(event, doubleClick)) {
            return true;
        }

        return false;
    }

    @Override
    public ScreenRectangle brbe$getTopLayerOverlayBounds() {
        if (this.brbe$hasTopLayerOverlay()) {
            return ((SmithingRecipeBookPage) this._$recipeBookComponent.recipesPage).overlay.getBounds();
        }

        return null;
    }

    @Override
    protected void slotClicked(Slot slot, int x, int y, ClickType clickType) {
        // clear ghost recipe if an empty ingredient slot is clicked with no items
        if (BetterRecipeBook.config.enableBook && slot != null && slot.index < 4 && menu.getCarried().isEmpty() && menu.slots.get(slot.index).getItem().isEmpty()) {
            _$recipeBookComponent.ghostRecipe.clear();
        }

        super.slotClicked(slot, x, y, clickType);
    }

    @Override
    protected boolean hasClickedOutside(double d, double e, int i, int j) {
        boolean bl = d < (double) i || e < (double) j || d >= (double) (i + this.imageWidth) || e >= (double) (j + this.imageHeight);
        return this._$recipeBookComponent.hasClickedOutside(d, e, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, 0) && bl;
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        if (this._$recipeBookComponent.isVisible()) {
            this._$recipeBookComponent.render(guiGraphics, i, j, f);
            this._$recipeBookComponent.renderGhostRecipe(guiGraphics, this.leftPos, this.topPos, false, f);
            this._$recipeBookComponent.drawTooltip(guiGraphics, this.leftPos, this.topPos, i, j);
        }
    }

    @Redirect(method = "renderBg", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/CyclingSlotBackground;render(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    public void renderBg(CyclingSlotBackground instance, AbstractContainerMenu slot, GuiGraphics bl, float g, int k, int arg) {
        if (!BetterRecipeBook.config.enableBook || !_$recipeBookComponent.isShowingGhostRecipe()) {
            instance.render(this.menu, bl, g, this.leftPos, this.topPos);
        }

        // pass, cancel render of onboarding tip slots if there is a ghost recipe
    }

    @Inject(method = "renderOnboardingTooltips", at = @At(value = "HEAD"), cancellable = true)
    public void renderOnboardingTooltips(GuiGraphics guiGraphics, int i, int j, CallbackInfo ci) {
        if (BetterRecipeBook.config.enableBook && _$recipeBookComponent.isShowingGhostRecipe()) {
            ci.cancel();
        }
    }

    @Inject(method = "slotChanged", at = @At(value = "HEAD"))
    public void slotChanged(AbstractContainerMenu abstractContainerMenu, int i, ItemStack itemStack, CallbackInfo ci) {
        if (i == SmithingMenu.BASE_SLOT || i == SmithingMenu.ADDITIONAL_SLOT || i == SmithingMenu.TEMPLATE_SLOT || i == SmithingMenu.RESULT_SLOT) {
            _$recipeBookComponent.ghostRecipe.clear();
        }
    }
}
