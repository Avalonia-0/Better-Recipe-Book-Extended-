package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「替代配方组开着时 LEI 完全无法操作」修复（用户 2026-09-13）。
 *
 * <p>原版 {@code RecipeBookPage.mouseClicked} 的语义是：只要替代配方组
 * （{@code OverlayRecipeComponent}）可见，这一击就归它——点到组内按钮就选配方，
 * 其余一律 {@code setVisible(false)} 关闭组，并且**无条件返回 true**。1.21.1 里
 * 各个配方书界面（{@code CraftingScreen.mouseClicked} 等）都是**先调
 * {@code recipeBookComponent.mouseClicked}、再轮到 {@code super.mouseClicked}**，
 * 而 LEI 的点击处理挂在 {@code AbstractContainerScreen.mouseClicked} —— 于是替代
 * 配方组一开，点任何地方都被配方书吞掉，LEI 的查询窗口/预览/pin 全部收不到点击
 * （26.2 / 1.21.11 的等价问题是 pins.AbstractContainerScreenMixin 抢在
 * recipeviewer.AbstractRecipeBookScreenMixin 之前吞点击，同样修掉）。</p>
 *
 * <p>这里把 LEI 的点击判定**提前到配方书之前**（只在指针确实落在 LEI 浮层上时，
 * 用与滚轮/关闭保护同一个 {@link RecipeViewerOverlay#modalMaskOwnsCursor}）：命中就
 * 直接调 LEI 的点击处理并消费这一击（LEI 浮层是硬模态，浮层内的点击本来就归它，
 * 与 26.2 的 HEAD 处理语义一致），未命中则完全交给原版配方书，行为不变。</p>
 *
 * <p>{@code priority = 1500}：确保本注入器排在该方法其它 HEAD 注入器
 * （instantcraft / search 等，默认 1000）之前——LEI 先挑。</p>
 */
@Mixin(value = RecipeBookComponent.class, priority = 1500)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerClickFirst(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!RecipeViewerOverlay.modalMaskOwnsCursor(Mth.floor(mouseX), Mth.floor(mouseY))) {
            return;
        }
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        PinOverlayManager.handleMouseClicked(mouseX, mouseY, button, screen);
        RecipeViewerOverlay.mouseClicked(mouseX, mouseY, button, screen);
        cir.setReturnValue(true);
    }
}
