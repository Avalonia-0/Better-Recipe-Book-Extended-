package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.layout.BookLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
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
    private StateSwitchingButton forwardButton;
    @Shadow
    private StateSwitchingButton backButton;

    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/StateSwitchingButton;mouseClicked(DDI)Z"), cancellable = true)
    public void mouseClickedBtn(double mouseX, double mouseY, int button, int areaLeft, int areaTop, int areaWidth, int areaHeight, CallbackInfoReturnable<Boolean> cir) {
        if (forwardButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            cir.cancel();
            if (++currentPage >= totalPages) {
                currentPage = BetterRecipeBook.config.scrollAround ? 0 : totalPages - 1;
            }
            updateButtonsForPage();
        } else if (backButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            cir.cancel();
            if (--currentPage < 0) {
                currentPage = BetterRecipeBook.config.scrollAround ? totalPages - 1 : 0;
            }
            updateButtonsForPage();
        }
    }

    @Inject(at = @At("HEAD"), method = "render")
    public void render(GuiGraphics gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        if (BetterRecipeBook.getQueuedScroll() != 0 && true) {
            if (isMouseOverRecipeBookPage(k, l, i, j) && totalPages > 1) {
                currentPage += BetterRecipeBook.getQueuedScroll();
                if (currentPage >= totalPages) {
                    currentPage = BetterRecipeBook.config.scrollAround ? currentPage % totalPages : totalPages - 1;
                } else if (currentPage < 0) {
                    currentPage = BetterRecipeBook.config.scrollAround ? (currentPage % totalPages) + totalPages : 0;
                }

                updateButtonsForPage();
            }
            BetterRecipeBook.setQueuedScroll(0);
        }
    }

    private static boolean isMouseOverRecipeBookPage(int mouseX, int mouseY, int left, int top) {
        return mouseX >= left && mouseX < left + BookLayout.TEXTURE_WIDTH
                && mouseY >= top && mouseY < top + BookLayout.TEXTURE_HEIGHT;
    }

    @Inject(at = @At("RETURN"), method = "init")
    public void init(Minecraft minecraftClient, int parentLeft, int parentTop, CallbackInfo ci) {
        BetterRecipeBook.setQueuedScroll(0);
    }

    @Inject(method = "updateArrowButtons", at = @At("RETURN"))
    private void updateArrowButtons(CallbackInfo ci) {
        if (BetterRecipeBook.config.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
        }
    }
}
