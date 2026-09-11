package com.alonie.brbe.mixins.recipebook;

import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版缺陷修复：配方书切换按钮（{@link ImageButton}，20×18，
 * {@code WidgetSprites.get(isHoveredOrFocused)} 高亮）点击后保留键盘焦点——
 * 鼠标移开按钮仍保持"选中"高亮，直到与配方书内元素交互（焦点转移）才取消。
 *
 * <p>焦点是在容器分发完点击（子部件 {@code onClick} 返回）之后才设置的
 * （{@code ContainerEventHandler.mouseClicked} 的 {@code setFocused}），因此
 * 在 {@code mouseClicked} 的 RETURN（整个分发链结束后）清除切换按钮的焦点即可：
 * 高亮从此只跟随鼠标悬停。清除对非点击路径无害（未聚焦时 setFocused(false)
 * 为空操作）。
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookToggleFocusMixin extends Screen {

    protected RecipeBookToggleFocusMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void brbe$clearToggleButtonFocus(MouseButtonEvent event, boolean doubleClick,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != Boolean.TRUE) return;
        for (GuiEventListener child : children()) {
            if (child instanceof ImageButton imageButton) {
                imageButton.setFocused(false);
                return;
            }
        }
    }
}
