package com.alonie.brbe.mixins.soundoptions;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.OptionsSubScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「音乐与声音」界面追加一个翻页音效音量滑块。
 *
 * <p>MC 1.21.11 的 {@code OptionInstance} 没有为自定义选项创建 double 滑块的
 * 公共 API：{@code ValueSet} / {@code SliderableValueSet} / {@code UnitDouble}
 * 都是包私有，无法从外部构造 {@code OptionInstance}。因此这里直接向
 * {@code SoundOptionsScreen.addOptions()} 返回后的 {@code OptionsList} 追加
 * 自建的 {@link AbstractSliderButton}（与一个占位控件配对成两列布局），
 * 样式与原生音量源一致。值落到 {@code zzzbrbe.toml}（{@code pageFlipVolume}），
 * 不污染 options.txt。</p>
 *
 * <p>滑块 0–1 ↔ 音量 0–1.5（默认 1.0 = 原生音量，翻页音效用 0.25 系数），
 * 与 26.2 的 UnitDouble.xmap 版本行为一致。</p>
 */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    /** 滑块 0–1 ↔ 音量 0–1.5 的放大系数（与 26.2 的 xmap 一致）。 */
    private static final float VOLUME_SCALE = 1.5f;

    private static final Component CAPTION = Component.translatable("soundCategory.zzzbrbe_page_flip");

    @Inject(method = "addOptions", at = @At("RETURN"))
    private void brbe$appendPageFlipVolume(CallbackInfo ci) {
        if (BetterRecipeBook.config == null) {
            return;
        }
        OptionsList list = ((OptionsSubScreenAccessor) (Object) this).brbe$getList();
        if (list == null) {
            return;
        }
        double initial = Math.max(0.0, Math.min(1.0, BetterRecipeBook.config.pageFlipVolume / VOLUME_SCALE));
        list.addSmall(
                new PageFlipVolumeSlider(initial),
                new BlankWidget());
    }

    /**
     * 复刻 {@code Options.percentValueOrOffLabel}（private，mixin 无法引用）：
     * 0% 显示"关闭"，否则显示百分比。
     */
    private static Component brbe$percentValueOrOffLabel(Component caption, double value) {
        if (value == 0.0) {
            return net.minecraft.client.Options.genericValueLabel(caption, CommonComponents.OPTION_OFF);
        }
        return Component.translatable("options.percent_value", caption, (int) (value * 100.0));
    }

    private static final class PageFlipVolumeSlider extends AbstractSliderButton {

        private final double volumeScale;

        PageFlipVolumeSlider(double initialValue) {
            super(0, 0, 150, AbstractSliderButton.DEFAULT_HEIGHT, CAPTION, initialValue);
            this.volumeScale = VOLUME_SCALE;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(brbe$percentValueOrOffLabel(CAPTION, this.value * this.volumeScale));
        }

        @Override
        protected void applyValue() {
            BetterRecipeBook.config.pageFlipVolume = (float) (this.value * this.volumeScale);
            try {
                BetterRecipeBook.configHolder.save();
            } catch (Exception ignored) {
                // Cloth Config 缺失/保存失败时保持内存值，滑块仍即时生效。
            }
        }
    }

    /** 与滑块配对的空白占位控件（addSmall 双列布局需要两个控件）。 */
    private static final class BlankWidget extends AbstractWidget {

        BlankWidget() {
            super(0, 0, 0, 0, Component.empty());
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
        }
    }
}
