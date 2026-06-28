package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds expanded recipe book support to the vanilla {@link RecipeBookComponent}.
 */
@Mixin(RecipeBookComponent.class)
public abstract class ExpandedBookMixin {

    @Shadow protected Minecraft minecraft;
    @Shadow private int xOffset;
    @Shadow private boolean widthTooNarrow;
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private EditBox searchBox;
    @Shadow private List<RecipeBookTabButton> tabButtons;
    @Shadow private boolean visible;

    // ── Expanded-mode helpers ────────────────────────────────────

    @Unique
    private boolean brbe$isExpanded() {
        return BetterRecipeBook.config.expandedRecipeBook
                && !this.widthTooNarrow
                && this.visible;
    }

    @Unique
    private int brbe$getBookWidth() {
        if (brbe$isExpanded()) {
            int invImageWidth = 176;
            int leftPos = ((RecipeBookComponentAccessor) this)
                    .updateScreenPositionInvoker(this.width, invImageWidth);
            int bookLeft = (this.width - 147) / 2 - this.xOffset;
            return (leftPos + invImageWidth) - bookLeft;
        }
        return 147;
    }

    // ── initVisuals: reposition for expanded width ────────────────

    @Inject(method = "initVisuals", at = @At("RETURN"))
    private void brbe$expandInitVisuals(CallbackInfo ci) {
        if (!brbe$isExpanded()) return;

        int bookWidth = brbe$getBookWidth();
        int bookX = (this.width - bookWidth) / 2 - this.xOffset;
        int bookY = (this.height - 166) / 2;

        // Widen search box
        if (this.searchBox != null) {
            this.searchBox.setWidth(bookWidth - 140);
        }

        // Reposition tab buttons (left column stays at bookX - 30)
        int tabX = bookX - 30;
        int tabY = bookY + 3;
        int slot = 0;
        for (RecipeBookTabButton btn : this.tabButtons) {
            btn.setX(tabX);
            btn.setY(tabY + 27 * slot++);
        }
    }

    // ── render: stretched background via Redirect ─────────────────

    @Unique
    private static final ResourceLocation RECIPE_BOOK_BG =
            ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
            require = 0)
    private void brbe$stretchBackground(GuiGraphics gui,
                                         ResourceLocation texture,
                                         int x, int y,
                                         int u0, int v0,
                                         int w, int h) {
        if (brbe$isExpanded() && RECIPE_BOOK_BG.equals(texture)) {
            int bookWidth = brbe$getBookWidth();
            int blitX = (this.width - bookWidth) / 2 - this.xOffset;
            int blitY = (this.height - 166) / 2;
            gui.blit(texture, blitX, blitY,
                    bookWidth, 166,
                    0, 0, 147, 166,
                    256, 256);
        } else {
            gui.blit(texture, x, y, u0, v0, w, h);
        }
    }

    // ── isOffsetNextToMainGUI: expanded mode is always "next to gui" ─

    @Inject(method = "isOffsetNextToMainGUI", at = @At("RETURN"), cancellable = true)
    private void brbe$isOffsetNextToMainGui(CallbackInfoReturnable<Boolean> cir) {
        if (brbe$isExpanded()) {
            cir.setReturnValue(true);
        }
    }

    // ── hasClickedOutside: use expanded width ────────────────────

    @Inject(method = "hasClickedOutside", at = @At("RETURN"), cancellable = true)
    private void brbe$hasClickedOutside(double d, double e, int i, int j, int k, int l, int m,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!brbe$isExpanded() || !this.visible) return;
        int bookWidth = brbe$getBookWidth();
        boolean bl = d < (double) i || e < (double) j || d >= (double) (i + k) || e >= (double) (j + l);
        boolean bl2 = (double) (i - bookWidth) < d && d < (double) i && (double) j < e && e < (double) (j + l);
        cir.setReturnValue(bl && !bl2);
    }
}
