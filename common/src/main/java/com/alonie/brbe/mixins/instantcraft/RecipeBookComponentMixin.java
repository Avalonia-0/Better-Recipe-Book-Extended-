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

        // Read actual positions from the page at runtime — do NOT compute
        // bookLeft/bookTop manually.  The page already has correctly-placed
        // buttons (positioned by GenericRecipePage).
        // Constraints: right edge = recipe grid right edge,
        //              bottom = page forward arrow bottom.
        com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor pageAcc =
                (com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor)(Object) recipeBookPage;
        var fwd = pageAcc.getForwardButton();

        // Find grid right edge from the rightmost visible recipe button
        int gridRight = 0;
        for (var btn : pageAcc.getButtons()) {
            if (btn.visible) {
                int r = btn.getX() + btn.getWidth();
                if (r > gridRight) gridRight = r;
            }
        }
        if (gridRight == 0) {
            // Fallback: no buttons yet — derive from book dimensions
            int bw = brbe$getExpandedBookWidth();
            int bl = (this.width - bw) / 2 - this.xOffset;
            gridRight = bl + bw - BookLayout.GRID_PAD;
        }
        int btnX = gridRight - 26; // button width 26

        // Y: bottom-aligned with forward arrow
        int btnY;
        if (fwd != null) {
            btnY = fwd.getY() + fwd.getHeight() - 18;
        } else {
            int bt = (this.height - 166) / 2;
            btnY = bt + 137 + 17 - 18;
        }

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
