package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts A/R/U keys on 1.21.1 container screens when config is enabled.
 * 1.21.1 uses raw (int, int, int) for key events instead of KeyEvent record.
 *
 * <p>R/U key matching delegates to each recipe viewer's own configured key
 * bindings via {@link ItemViewCompat} — no hardcoded key codes for recipe/usage.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // GLFW key constant for A (REI/JEI favorites)
    private static final int KEY_A = 65;

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$handleKeysOnHiddenOverlay(int keyCode, int scancode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // BRBE 自研查询浮层优先：R/U 键直接打开烘焙引擎（不依赖外部 viewer）。
        // 独立于 hideReiJeiOverlay——查询功能由 recipeViewerEnabled 配置控制。
        // ESC 关闭查询浮层（下层屏幕保持不关闭——viewer 是模态俯层）。
        if (com.alonie.brbe.util.RecipeViewerOverlay.isActive() && keyCode == 256) {
            com.alonie.brbe.util.RecipeViewerOverlay.close();
            cir.setReturnValue(true);
            return;
        }
        if (BetterRecipeBook.ctx().config().recipeViewerEnabled
                && RecipeViewerOverlay.keyPressed(keyCode, scancode, modifiers,
                        (AbstractContainerScreen<?>) (Object) this, this.hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (!BetterRecipeBook.ctx().config().hideReiJeiOverlay) {
            return;
        }

        // A key: prevent REI/JEI favorites. Skip if text field is focused.
        if (RecipeViewerOverlay.keyPressed(keyCode, scancode, modifiers,
                (AbstractContainerScreen<?>) (Object) this, this.hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        // A key: prevent REI/JEI favorites. Skip if text field is focused.
        if (keyCode == KEY_A) {
            Screen screen = (Screen) (Object) this;
            if (!(screen.getFocused() instanceof EditBox)) {
                cir.setReturnValue(true);
            }
            return;
        }

        // R / U: route to the active recipe viewer (delegates to viewer's own key config)
        if (!ItemViewCompat.matchesShowRecipe(keyCode, scancode)
                && !ItemViewCompat.matchesShowUses(keyCode, scancode)) {
            return;
        }

        if (!ItemViewCompat.isLoaded()) {
            return;
        }

        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack stack = slot.getItem();
        boolean handled = ItemViewCompat.matchesShowRecipe(keyCode, scancode)
                ? ItemViewCompat.openRecipeView(stack)
                : ItemViewCompat.openUsageView(stack);

        if (handled) {
            cir.setReturnValue(true);
        }
    }

    /** 查询浮层点击：viewer 打开的模态层吞掉所有点击（框内交互/框外关闭）。 */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseClicked(double mouseX, double mouseY, int button,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.mouseClicked(mouseX, mouseY, button,
                (AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    /** 查询浮层滚轮：viewer 打开的模态层吞掉滚轮（翻页/切标签/滑工作站列）。 */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerMouseScrolled(double mouseX, double mouseY, double horizontal,
                                          double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.mouseScrolled(mouseX, mouseY, vertical)) {
            cir.setReturnValue(true);
        }
    }

    /** viewer 打开时抑制容器槽位 tooltip（viewer 自己的 tooltip 在最上层绘制）。 */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void brbe$suppressSlotTooltipWhileViewer(GuiGraphics gui, int mouseX, int mouseY,
                                                     CallbackInfo ci) {
        if (RecipeViewerOverlay.isActive()) {
            ci.cancel();
        }
    }
}
