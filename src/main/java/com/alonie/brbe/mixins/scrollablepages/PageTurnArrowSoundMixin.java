package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PageTurnArrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配方书「上一页 / 下一页」箭头按下时改播**翻页音效**（配置项 {@code pageFlipSound} +
 * 「音效音量」），而不是 vanilla 的原版按钮点击声。
 *
 * <p>为什么挂 {@code AbstractWidget.playDownSound}：所有箭头点击路径最终都会走到
 * {@code AbstractWidget.mouseClicked} → {@code playDownSound}
 * （原版 {@code RecipeBookPage.mouseClicked}、BRBE 的 scrollAround 拦截、Ctrl+跳页、
 * BRBE 自研酿造/锻造书的翻页），在这里替换是**唯一**能一次覆盖全部路径的点。</p>
 *
 * <p>哪些 widget 算箭头由 {@link PageTurnArrows} 登记（书页在构造/刷新箭头时登记）——
 * 只影响这些 widget，其它按钮照旧播原版点击声。</p>
 */
@Mixin(AbstractWidget.class)
public abstract class PageTurnArrowSoundMixin {

    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void brbe$pageTurnFlipSound(SoundManager soundManager, CallbackInfo ci) {
        if (!PageTurnArrows.isPageArrow((AbstractWidget) (Object) this)) return;
        // 吞掉原版点击声，改用翻页音效（翻页音效自己带音量换算与开关判断）。
        ci.cancel();
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
    }
}
