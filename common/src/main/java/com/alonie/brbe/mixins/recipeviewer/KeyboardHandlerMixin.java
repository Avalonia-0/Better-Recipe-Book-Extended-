package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 版键盘层优先 mixin（1.21.11 KeyboardHandlerMixin priority 2000 移植）。
 *
 * <p>1.21.1 的 {@code KeyboardHandler.keyPress(long, int, int, int, int)} 签名
 * （window, key, scancode, action, modifiers——无 KeyEvent record）；真实 JEI 的
 * allowKeyPress 前置事件在 Fabric 从 KeyboardHandler 先于 Screen.keyPressed 触发，
 * R/U 会被 JEI 截走——这里在键盘层入口（最高优先级）先处理，viewer 打不开时
 * 照常透传。</p>
 */
@Mixin(value = KeyboardHandler.class, priority = 2000)
public abstract class KeyboardHandlerMixin {

    /** 当前按键周期内已消费的 GLFW 键码（防 OS 重复/释放重入）。 */
    @Unique
    private static int brbe$activeKeyCode = -1;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void brbe$viewerKeysEarly(long window, int key, int scancode, int action,
                                      int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            brbe$activeKeyCode = -1;
            return;
        }
        if (!InputConstants.isKeyDown(mc.getWindow().getWindow(), key)) {
            // 释放已消费的键：结束本次按键周期。
            if (key == brbe$activeKeyCode) {
                brbe$activeKeyCode = -1;
            }
            return;
        }
        if (key == brbe$activeKeyCode) {
            // OS 键重复：本周期已消费，跳过。
            return;
        }
        if (RecipeViewerOverlay.keyPressed(key, scancode, modifiers, containerScreen)
                || PinOverlayManager.handleKeyPressed(key, scancode, modifiers, containerScreen)) {
            brbe$activeKeyCode = key;
            ci.cancel();
        }
    }
}
