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

    @Shadow
    private net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent overlay;

    /**
     * 翻页（点击/滚轮/updateCollections 等所有路径都会走到
     * updateButtonsForPage）后关闭替代配方 overlay，否则它会留在原地。
     */
    @Inject(method = "updateButtonsForPage", at = @At("RETURN"))
    private void brbe$closeOverlayOnPageChange(CallbackInfo ci) {
        this.overlay.setVisible(false);
    }

    /**
     * Ctrl+点击翻页箭头直接跳到首页/尾页（HEAD 拦截，优先于原版逐页翻页）。
     * 1.21.1 版：mouseClicked(double,double,int) 签名。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void brbe$mouseClickedJumpToEdge(double mouseX, double mouseY, int button,
                                            int areaLeft, int areaTop, int areaWidth, int areaHeight,
                                            boolean widthTooNarrow, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !com.alonie.brbe.util.ClientCompat.isControlDown()) {
            return;
        }
        if (forwardButton.mouseClicked(mouseX, mouseY, button)) {
            if (currentPage < totalPages - 1) {
                com.alonie.brbe.util.RecipeBookPageAnimBridge.markUserFlip();
                currentPage = totalPages - 1;
                updateButtonsForPage();
            }
            cir.setReturnValue(true);
            return;
        }
        if (backButton.mouseClicked(mouseX, mouseY, button)) {
            if (currentPage > 0) {
                com.alonie.brbe.util.RecipeBookPageAnimBridge.markUserFlip();
                currentPage = 0;
                updateButtonsForPage();
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/StateSwitchingButton;mouseClicked(DDI)Z"), cancellable = true)
    public void mouseClickedBtn(double mouseX, double mouseY, int button, int areaLeft, int areaTop, int areaWidth, int areaHeight, CallbackInfoReturnable<Boolean> cir) {
        if (forwardButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            cir.cancel();
            if (++currentPage >= totalPages) {
                currentPage = BetterRecipeBook.config.scrolling.scrollAround ? 0 : totalPages - 1;
            }
            updateButtonsForPage();
        } else if (backButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            cir.cancel();
            if (--currentPage < 0) {
                currentPage = BetterRecipeBook.config.scrolling.scrollAround ? totalPages - 1 : 0;
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
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? currentPage % totalPages : totalPages - 1;
                } else if (currentPage < 0) {
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? (currentPage % totalPages) + totalPages : 0;
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
        if (BetterRecipeBook.config.scrolling.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
        }
    }
}
