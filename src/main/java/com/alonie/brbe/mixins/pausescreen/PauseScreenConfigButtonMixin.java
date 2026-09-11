package com.alonie.brbe.mixins.pausescreen;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 暂停界面右上角插入一个 20×20 方形图标按钮，点击打开 BRBE 配置界面。
 *
 * <p><b>与 26.2 的布局差异（版本 API 所致，非功能缺失）</b>：26.2 的暂停菜单多了一条
 * "图标行"（bug 反馈/社交/好友/举报/Mod Menu 那一行，
 * {@code createPauseMenu} 里调用 {@code LinearLayout.horizontal()}），26.2 的
 * {@code PauseScreenConfigButtonMixin} 用 {@code @Redirect} 拦下该行并把本按钮挂到行首。
 * **1.21.11 的 {@code createPauseMenu} 仍是经典 {@code GridLayout + RowHelper} 两列布局，
 * 没有图标行**（{@code addFeedbackButtons(Screen, RowHelper)} 直接把反馈按钮塞进网格行），
 * 因此这里改为在方法 TAIL 追加一个独立部件，放在屏幕右上角——该位置原版不占用，
 * 也不会与居中的标题（y≈40）或网格冲突。</p>
 *
 * <p>刻意<b>不用</b> {@code LocalCapture} 拿 {@code RowHelper}（那样能把按钮塞进网格行）：
 * Mod Menu 等 Mixin 会改写该方法的作用域，{@code LocalCapture} 会失败——
 * 与 26.2 实现同源的取舍。</p>
 *
 * <p>配置项 {@code hidePauseMenuConfigEntry} 开启时完全不添加该按钮。</p>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenConfigButtonMixin extends Screen {

    protected PauseScreenConfigButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void brbe$addConfigButton(CallbackInfo ci) {
        if (BetterRecipeBook.config.hidePauseMenuConfigEntry) return;
        Component message = Component.translatable("text.autoconfig.brbe.title");
        SpriteIconButton button = SpriteIconButton.builder(
                        message,
                        b -> Minecraft.getInstance().setScreen(createConfigScreen(PauseScreenConfigButtonMixin.this)),
                        true)
                .size(20, 20)
                .sprite(Identifier.fromNamespaceAndPath("brbe", "pause_menu/brbe"), 20, 18)
                .withTootip()
                .build();
        button.setPosition(this.width - 24, 4);
        this.addRenderableWidget(button);
    }

    /** 构建 Cloth Config 配置屏（与 ModMenu 反射桥、书内设置按钮同源）。 */
    private static Screen createConfigScreen(Screen parent) {
        try {
            Supplier<Screen> supplier = me.shedaniel.autoconfig.AutoConfig
                    .getConfigScreen(BrbeConfig.class, parent);
            return supplier.get();
        } catch (Exception e) {
            return parent;
        }
    }
}
