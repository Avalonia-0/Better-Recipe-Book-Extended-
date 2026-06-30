package com.alonie.brbe.mixins.instantcraft;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.AbstractFurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow protected Minecraft minecraft;
    @Shadow private int height;
    @Shadow private int width;
    @Shadow private int xOffset;
    @Shadow private boolean widthTooNarrow;
    @Shadow @Final private RecipeBookPage recipeBookPage;

    @Shadow public abstract boolean isVisible();

    @Unique protected StateSwitchingButton brbe$instantCraftButton;
    @Unique private static final Component TOGGLE_INSTANT_CRAFT_ON_TEXT;
    @Unique private static final Component TOGGLE_INSTANT_CRAFT_OFF_TEXT;

    @Unique
    private int brbe$getExpandedBookWidth() {
        if (!BetterRecipeBook.ctx().config().expandedRecipeBook || this.widthTooNarrow || !isVisible())
            return BookLayout.TEXTURE_WIDTH;
        int leftPos = ((RecipeBookComponentAccessor) this)
                .updateScreenPositionInvoker(this.width, 176);
        int bookLeft = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        return (leftPos + 176) - bookLeft;
    }

    @Unique
    private boolean brbe$shouldSkip() {
        if (!BetterRecipeBook.ctx().config().instantCraft.showButton) {
            return true;
        }

        // remove instant craft button in furnaces
        return ((Object) this) instanceof AbstractFurnaceRecipeBookComponent;
    }

    @Inject(method = "initVisuals", at = @At("RETURN"))
    public void reset(CallbackInfo ci) {
        if (brbe$shouldSkip()) {
            return;
        }

        // Align with the right edge of the recipe grid area.
        // The filter button's right edge (= filterX + 26) matches the
        // recipe grid's right edge in both vanilla and expanded layouts.
        StateSwitchingButton filterBtn =
                ((RecipeBookComponentAccessor) this).getFilterButton();
        int btnX = filterBtn != null
                ? filterBtn.getX()
                : (this.width - 147) / 2 - this.xOffset + 110;
        int btnY = (this.height - 166) / 2 + 137;

        this.brbe$instantCraftButton = new StateSwitchingButton(
                btnX, btnY, 26, 18,
                BetterRecipeBook.instantCraftingManager.isEnabled());
        BetterRecipeBook.instantCraftingManager.lastInstantCraftButton = this.brbe$instantCraftButton;
        this.brbe$instantCraftButton.initTextureValues(BRBTextures.RECIPE_BOOK_INSTANT_CRAFT_BUTTON_SPRITES);
    }

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;render(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (brbe$shouldSkip() || this.brbe$instantCraftButton == null || !this.isVisible()) {
            return;
        }
        this.brbe$instantCraftButton.render(gui, mouseX, mouseY, delta);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.isVisible() && !brbe$shouldSkip() && this.brbe$instantCraftButton != null) {
            if (this.brbe$instantCraftButton.mouseClicked(mouseX, mouseY, button)) {
                boolean bl = BetterRecipeBook.instantCraftingManager.toggleEnabled();
                this.brbe$instantCraftButton.setStateTriggered(bl);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "renderTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;renderGhostRecipeTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"))
    public void drawTooltip(GuiGraphics gui, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        if (brbe$shouldSkip()) {
            return;
        }

        if (this.brbe$instantCraftButton != null && this.brbe$instantCraftButton.isHoveredOrFocused()) {
            Component text = this.brbe$instantCraftButton.isStateTriggered() ? TOGGLE_INSTANT_CRAFT_ON_TEXT : TOGGLE_INSTANT_CRAFT_OFF_TEXT;
            if (this.minecraft.screen != null) {
                gui.renderComponentTooltip(minecraft.font, List.of(text), mouseX, mouseY);
            }
        }
    }

    static {
        TOGGLE_INSTANT_CRAFT_ON_TEXT = Component.translatable("brb.gui.instantCraft.on");
        TOGGLE_INSTANT_CRAFT_OFF_TEXT = Component.translatable("brb.gui.instantCraft.off");
    }
}
