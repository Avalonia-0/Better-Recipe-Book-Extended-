package com.alonie.brbe.mixins.soundoptions;

import com.alonie.brbe.BetterRecipeBook;
import java.util.Arrays;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在「音乐与声音」界面追加一个翻页音效音量滑块。
 *
 * <p>MC 26.2 没有为自定义声音源注册滑块的 API —— 滑块列表由
 * {@code SoundOptionsScreen} 遍历 {@code SoundSource.values()} 生成。
 * 这里把滑块追加到 {@code getAllSoundOptionsExceptMaster()} 返回的数组，
 * 使其进入「其他声音」两列布局，样式与原生音量源一致。
 * 值落到 {@code brbe.toml}（{@code pageFlipVolume}），不污染 options.txt。</p>
 *
 * <p>注意：不要用 {@code @Shadow} 访问 {@code OptionsSubScreen.list}
 * （父类字段，Mixin 0.8.7 的 shadow 只认目标类自身的字段，会导致
 * MixinApplyError 崩溃）。</p>
 */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    @Inject(method = "getAllSoundOptionsExceptMaster", at = @At("RETURN"), cancellable = true)
    private void brbe$appendPageFlipVolume(CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        if (BetterRecipeBook.config == null) {
            return;
        }
        OptionInstance<?>[] original = cir.getReturnValue();
        OptionInstance<?>[] result = Arrays.copyOf(original, original.length + 1);
        // 范围 0–150%（UnitDouble 是 0–1 滑块，xmap 线性放大到 0–1.5）。
        result[original.length] = new OptionInstance<>(
                "soundCategory.brbe_page_flip",
                OptionInstance.noTooltip(),
                SoundOptionsScreenMixin::brbe$percentValueOrOffLabel,
                OptionInstance.UnitDouble.INSTANCE.xmap(v -> v * 1.5, v -> v / 1.5),
                (double) BetterRecipeBook.config.pageFlipVolume,
                value -> {
                    BetterRecipeBook.config.pageFlipVolume = value.floatValue();
                    try {
                        BetterRecipeBook.configHolder.save();
                    } catch (Exception ignored) {
                        // Cloth Config 缺失/保存失败时保持内存值，滑块仍即时生效。
                    }
                });
        cir.setReturnValue(result);
    }

    /**
     * 复刻 {@code Options.percentValueOrOffLabel}（private，mixin 无法引用）：
     * 0% 显示"关闭"，否则显示百分比。
     */
    private static Component brbe$percentValueOrOffLabel(Component caption, double value) {
        if (value == 0.0) {
            return Options.genericValueLabel(caption, CommonComponents.OPTION_OFF);
        }
        return Component.translatable("options.percent_value", caption, (int) (value * 100.0));
    }
}
