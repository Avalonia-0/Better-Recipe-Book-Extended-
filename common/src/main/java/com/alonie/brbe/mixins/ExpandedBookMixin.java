package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
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
    @Shadow private RecipeBookPage recipeBookPage;
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
        int bookX = (this.width - 147) / 2 - this.xOffset; // fixed left edge
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

        // Reposition recipe buttons to fill the expanded width (flex grid)
        int cols = Math.max(5, (bookWidth - 22) / 25);
        List<RecipeButton> buttons = ((RecipeBookPageAccessor) this.recipeBookPage).getButtons();
        for (int k = 0; k < buttons.size(); k++) {
            buttons.get(k).setX(bookX + 11 + 25 * (k % cols));
            buttons.get(k).setY(bookY + 31 + 25 * (k / cols));
        }
    }

    // ── render: stretched background via Redirect ─────────────────

    @Unique
    private static final ResourceLocation RECIPE_BOOK_BG =
            ResourceLocation.withDefaultNamespace("textures/gui/recipe_book.png");

    @Unique
    private static final int BG_LEFT_CAP = 32;
    @Unique
    private static final int BG_RIGHT_CAP = 12;
    @Unique
    private static final int BG_BODY = 103; // 147 - 32 - 12

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
            require = 0)
    private void brbe$expandBackground(GuiGraphics gui,
                                        ResourceLocation texture,
                                        int x, int y,
                                        int u0, int v0,
                                        int w, int h) {
        if (!brbe$isExpanded() || !RECIPE_BOOK_BG.equals(texture)) {
            gui.blit(texture, x, y, u0, v0, w, h);
            return;
        }

        int bookWidth = brbe$getBookWidth();
        // Left edge stays fixed at vanilla position; expansion goes right only
        int blitX = (this.width - 147) / 2 - this.xOffset;
        int blitY = (this.height - 166) / 2;

        // Left cap (7px) — border + shadows
        gui.blit(texture, blitX, blitY, 0, 0, BG_LEFT_CAP, 166, 256, 256);

        // Body — tile across the expanded gap
        int bodyStartX = blitX + BG_LEFT_CAP;
        int bodyEndX = blitX + bookWidth - BG_RIGHT_CAP;
        for (int bx = 0; bx < bodyEndX - bodyStartX; bx += BG_BODY) {
            int segW = Math.min(BG_BODY, bodyEndX - bodyStartX - bx);
            gui.blit(texture, bodyStartX + bx, blitY,
                    BG_LEFT_CAP, 0, segW, 166, 256, 256);
        }

        // Right cap (12px) — src X = 147 - 12 = 135
        gui.blit(texture, bodyEndX, blitY,
                135, 0, BG_RIGHT_CAP, 166, 256, 256);
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
