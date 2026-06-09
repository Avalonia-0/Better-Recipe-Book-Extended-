package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin {
    @Shadow
    private int currentPage;
    @Shadow
    private int totalPages;

    @Shadow
    protected abstract void updateButtonsForPage();

    @Shadow
    private ImageButton forwardButton;
    @Shadow
    private ImageButton backButton;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void mouseClickedBtn(MouseButtonEvent event, int areaLeft, int areaTop, int areaWidth, int areaHeight, boolean widthTooNarrow, CallbackInfoReturnable<Boolean> cir) {
        if (!BetterRecipeBook.config.scrolling.scrollAround || totalPages <= 1 || event.button() != 0) {
            return;
        }

        if (currentPage == totalPages - 1 && ClientCompat.mouseClicked(forwardButton, event.x(), event.y(), event.button())) {
            currentPage = 0;
            updateButtonsForPage();
            cir.setReturnValue(true);
            return;
        }

        if (currentPage == 0 && ClientCompat.mouseClicked(backButton, event.x(), event.y(), event.button())) {
            currentPage = totalPages - 1;
            updateButtonsForPage();
            cir.setReturnValue(true);
        }
    }

    @Inject(at = @At("RETURN"), method = "render")
    public void render(GuiGraphics gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        if (BetterRecipeBook.queuedScroll != 0 && BetterRecipeBook.config.scrolling.enableScrolling) {
            if (isMouseOverRecipeBookPage(k, l, i, j) && totalPages > 1) {
                currentPage += BetterRecipeBook.queuedScroll;
                if (currentPage >= totalPages) {
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? currentPage % totalPages : totalPages - 1;
                } else if (currentPage < 0) {
                    // required as % is not modulus, it is remainder. we need to force output positive by((currentPage % totalPages) + totalPages)
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? (currentPage % totalPages) + totalPages : 0;
                }

                updateButtonsForPage();
            }
            BetterRecipeBook.queuedScroll = 0;
        }
    }

    private static boolean isMouseOverRecipeBookPage(int mouseX, int mouseY, int left, int top) {
        return mouseX >= left && mouseX < left + 147 && mouseY >= top && mouseY < top + 166;
    }

    @Inject(at = @At("RETURN"), method = "init")
    public void init(Minecraft minecraftClient, int parentLeft, int parentTop, CallbackInfo ci) {
        BetterRecipeBook.queuedScroll = 0;
    }

    @Inject(method = "updateArrowButtons", at = @At("RETURN"))
    private void updateArrowButtons(CallbackInfo ci) {
        if (BetterRecipeBook.config.scrolling.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
            forwardButton.active = true;
            backButton.active = true;
        }
    }
}
